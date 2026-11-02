package project.ai.stream.reclaim;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.PendingMessagesSummary;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 스트림마다 PendingMessageReclaimer를 주기적으로 돌린다.
 *
 * 시작할 때 한 번 예전 PEL을 정리하고(cleanUpLegacy), 그 뒤로 interval마다 멈춘 메시지를 재처리하고
 * pending 지표를 갱신한다. 한 스레드에서 차례로 돌려, 같은 스트림을 두 주기가 동시에 건드리지 않는다.
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

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "stream-pel-reclaim");
        t.setDaemon(true);
        return t;
    });

    public StreamReclaimScheduler(List<PendingMessageReclaimer> reclaimers, List<StreamGroup> pendingOnly,
                                  StringRedisTemplate redis, StreamReclaimMetrics metrics,
                                  Duration legacyCutoff, Duration interval) {
        this.reclaimers = reclaimers;
        this.pendingOnly = pendingOnly;
        this.redis = redis;
        this.metrics = metrics;
        this.legacyCutoff = legacyCutoff;
        this.interval = interval;
    }

    public void start() {
        executor.execute(this::cleanUpLegacy);
        executor.scheduleWithFixedDelay(this::tick, interval.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS);
    }

    public void stop() {
        executor.shutdownNow();
    }

    void cleanUpLegacy() {
        long now = System.currentTimeMillis();
        for (PendingMessageReclaimer reclaimer : reclaimers) {
            try {
                int acked = reclaimer.cleanUpLegacy(legacyCutoff, now);
                long remaining = reclaimer.refreshPendingCount();
                log.info("예전 PEL 정리 - stream={}, 기준={}보다 오래된 {}건 ACK(재처리 안 함), 남은 pending={}(자동 재처리 대상)",
                    reclaimer.streamKey(), legacyCutoff, acked, remaining);
            } catch (Exception e) {
                // 시작이 막히지 않게 로그만 남긴다. 다음 시작 때 다시 정리한다
                log.error("예전 PEL 정리 실패 - stream={}", reclaimer.streamKey(), e);
            }
        }
    }

    void tick() {
        for (PendingMessageReclaimer reclaimer : reclaimers) {
            reclaimer.reclaimOnce();
            try {
                reclaimer.refreshPendingCount();
            } catch (Exception e) {
                log.warn("pending 수 확인 실패 - stream={}: {}", reclaimer.streamKey(), e.getMessage());
            }
        }
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
