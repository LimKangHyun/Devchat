package project.ai.stream.reclaim;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.PendingMessagesSummary;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 스트림마다 PendingMessageReclaimer를 주기적으로 돌린다.
 *
 * 스트림마다 따로 예약한 작업(스레드)에서 돈다. 한 스레드에서 차례로 돌리면, 한 스트림의 재처리가 오래 걸릴 때
 * 다른 스트림의 재처리와 pending 지표 갱신까지 멈춘다. 같은 스트림은 한 작업에서만 돌므로 두 주기가 겹치지 않는다.
 * 각 작업은 처음에 그 스트림의 예전 PEL을 정리하고(cleanUpLegacy, 처음 배포 때 한 번), 그 뒤 interval마다
 * 멈춘 메시지를 재처리하고 pending 지표를 갱신한다.
 *
 * AI 리뷰 스트림은 AiReviewPelReclaimScheduler가 재처리하므로 여기서는 pending 지표만 갱신한다(pendingOnly).
 */
@Slf4j
public class StreamReclaimScheduler {

    /** pending 지표만 갱신할 스트림 */
    public record StreamGroup(String streamKey, String group) {}

    private final List<PendingMessageReclaimer> reclaimers;
    private final List<StreamGroup> pendingOnly;
    private final StringRedisTemplate redis;
    private final StreamReclaimMetrics metrics;
    private final Duration legacyCutoff;
    private final Duration interval;
    private final ScheduledExecutorService executor;

    public StreamReclaimScheduler(List<PendingMessageReclaimer> reclaimers, List<StreamGroup> pendingOnly,
                                  StringRedisTemplate redis, StreamReclaimMetrics metrics,
                                  Duration legacyCutoff, Duration interval) {
        this.reclaimers = reclaimers;
        this.pendingOnly = pendingOnly;
        this.redis = redis;
        this.metrics = metrics;
        this.legacyCutoff = legacyCutoff;
        this.interval = interval;
        AtomicInteger seq = new AtomicInteger();
        this.executor = Executors.newScheduledThreadPool(reclaimers.size() + 1, r -> {
            Thread t = new Thread(r, "stream-pel-reclaim-" + seq.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        long period = interval.toMillis();
        for (PendingMessageReclaimer reclaimer : reclaimers) {
            // 첫 실행에서 예전 PEL을 정리하고, 그 뒤로 주기마다 재처리한다. 한 작업이라 둘이 겹치지 않는다
            executor.scheduleWithFixedDelay(new Runnable() {
                private boolean legacyCleaned;

                @Override
                public void run() {
                    if (!legacyCleaned) {
                        legacyCleaned = true;
                        cleanUpLegacy(reclaimer);
                    }
                    tick(reclaimer);
                }
            }, 0, period, TimeUnit.MILLISECONDS);
        }
        executor.scheduleWithFixedDelay(this::refreshPendingOnly, 0, period, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        executor.shutdownNow();
    }

    void cleanUpLegacy(PendingMessageReclaimer reclaimer) {
        try {
            int acked = reclaimer.cleanUpLegacy(legacyCutoff, System.currentTimeMillis());
            if (acked == PendingMessageReclaimer.LEGACY_ALREADY_DONE) {
                log.info("예전 PEL 정리는 이미 끝남 - stream={}", reclaimer.streamKey());
            } else {
                log.info("예전 PEL 정리 - stream={}, 기준={}보다 오래된 {}건 ACK(재처리 안 함), 남은 pending={}(자동 재처리 대상)",
                    reclaimer.streamKey(), legacyCutoff, acked, reclaimer.refreshPendingCount());
            }
        } catch (Exception e) {
            // 시작이 막히지 않게 로그만 남긴다. 다음 시작 때 다시 정리한다
            log.error("예전 PEL 정리 실패 - stream={}", reclaimer.streamKey(), e);
        }
    }

    void tick(PendingMessageReclaimer reclaimer) {
        reclaimer.reclaimOnce();
        try {
            reclaimer.refreshPendingCount();
        } catch (Exception e) {
            log.warn("pending 수 확인 실패 - stream={}: {}", reclaimer.streamKey(), e.getMessage());
        }
    }

    void refreshPendingOnly() {
        for (StreamGroup sg : pendingOnly) {
            try {
                PendingMessagesSummary summary = redis.opsForStream().pending(sg.streamKey(), sg.group());
                metrics.pending(sg.streamKey(), summary == null ? 0 : summary.getTotalPendingMessages());
            } catch (Exception e) {
                log.warn("pending 수 확인 실패 - stream={}: {}", sg.streamKey(), e.getMessage());
            }
        }
    }
}
