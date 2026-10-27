package project.api.global.outbox;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * outbox_event를 주기적으로 읽어 보낸다 (폴링 릴레이).
 *
 * 처리 순서: 짧은 트랜잭션으로 행을 잠그고 lease를 건 뒤 → 트랜잭션 밖에서 처리 → 성공이면 SENT, 실패면 백오프 후 재시도.
 * 같은 이벤트가 두 번 처리될 수 있어(at-least-once) 각 처리기는 중복에 안전해야 한다.
 * AI 리뷰 요청은 AI 서버의 claim과 API 서버의 조건부 UPDATE가 중복을 걸러낸다.
 */
@Slf4j
@Component
public class OutboxRelay {

    private static final int BATCH_SIZE = 50;
    private static final Duration KEEP_SENT = Duration.ofDays(7);

    private final OutboxService outboxService;
    private final OutboxEventRepository repository;
    private final MeterRegistry meterRegistry;
    private final Map<OutboxEventType, OutboxHandler> handlers = new EnumMap<>(OutboxEventType.class);

    private final AtomicLong pendingCount = new AtomicLong();
    private final AtomicLong oldestPendingSeconds = new AtomicLong();

    public OutboxRelay(OutboxService outboxService, OutboxEventRepository repository,
                       MeterRegistry meterRegistry, List<OutboxHandler> handlerList) {
        this.outboxService = outboxService;
        this.repository = repository;
        this.meterRegistry = meterRegistry;
        handlerList.forEach(h -> handlers.put(h.type(), h));
    }

    @PostConstruct
    void registerMeters() {
        Gauge.builder("outbox.pending", pendingCount, AtomicLong::get)
            .description("보내지 못하고 기다리는 outbox 이벤트 수").register(meterRegistry);
        Gauge.builder("outbox.oldest.pending.seconds", oldestPendingSeconds, AtomicLong::get)
            .description("가장 오래 기다린 outbox 이벤트의 대기 시간").register(meterRegistry);
        for (OutboxEventType type : OutboxEventType.values()) {
            for (String result : new String[]{"sent", "retry", "failed"}) {
                meterRegistry.counter("outbox.events", "type", type.name(), "result", result);
            }
        }
    }

    @Scheduled(fixedDelayString = "${outbox.relay.interval-ms:1000}")
    public void relay() {
        List<OutboxEvent> due;
        try {
            due = outboxService.leaseDue(BATCH_SIZE);
        } catch (Exception e) {
            log.warn("outbox 조회 실패, 다음 주기에 다시 시도", e);
            return;
        }
        due.forEach(this::dispatch);
    }

    void dispatch(OutboxEvent event) {
        OutboxHandler handler = handlers.get(event.getEventType());
        try {
            if (handler == null) {
                throw new IllegalStateException("처리기 없음: " + event.getEventType());
            }
            handler.handle(event);
            outboxService.markSent(event.getId());
            count(event, "sent");
        } catch (Exception e) {
            String error = e.getClass().getSimpleName() + ": " + e.getMessage();
            boolean gaveUp = outboxService.recordFailure(event.getId(), error);
            if (!gaveUp) {
                log.warn("outbox 처리 실패, 다시 시도 - id={}, type={}, aggregateId={}, attempts={}",
                    event.getId(), event.getEventType(), event.getAggregateId(), event.getAttempts(), e);
                count(event, "retry");
                return;
            }
            log.error("outbox 처리 포기 - id={}, type={}, aggregateId={}", event.getId(), event.getEventType(),
                event.getAggregateId(), e);
            count(event, "failed");
            if (handler != null) {
                try {
                    handler.onGiveUp(event, error);
                } catch (Exception giveUpError) {
                    log.error("outbox 포기 처리 실패 - id={}", event.getId(), giveUpError);
                }
            }
        }
    }

    /** Grafana: 대기 중인 이벤트 수와 가장 오래된 대기 시간. 릴레이가 멈추면 이 값이 계속 커진다 */
    @Scheduled(fixedDelay = 15_000)
    public void refreshGauges() {
        try {
            pendingCount.set(repository.countByStatus(OutboxStatus.PENDING));
            LocalDateTime oldest = repository.findOldestCreatedAt(OutboxStatus.PENDING);
            oldestPendingSeconds.set(oldest == null ? 0 : Duration.between(oldest, LocalDateTime.now()).toSeconds());
        } catch (Exception e) {
            log.debug("outbox 지표 갱신 실패", e);
        }
    }

    /** 보낸 지 7일 지난 행을 지운다 (한 번에 1000개씩) */
    @Scheduled(cron = "0 30 4 * * *")
    public void cleanUp() {
        int deleted;
        int total = 0;
        do {
            deleted = outboxService.deleteSentBefore(LocalDateTime.now().minus(KEEP_SENT));
            total += deleted;
        } while (deleted > 0);
        if (total > 0) log.info("outbox 정리: {}건 삭제", total);
    }

    private void count(OutboxEvent event, String result) {
        meterRegistry.counter("outbox.events", "type", event.getEventType().name(), "result", result).increment();
    }
}
