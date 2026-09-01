package project.api.domain.chat.chatroom.app;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatRoomCheckpointScheduler {

    private final ChatRoomSyncService chatRoomSyncService;
    private final MeterRegistry meterRegistry;

    @Scheduled(fixedDelay = 30_000)
    public void syncCheckpoints() {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            chatRoomSyncService.syncToDb();
            meterRegistry.counter("checkpoint.scheduler.success", "type", "sync").increment();
        } catch (Exception e) {
            log.error("checkpoint 동기화 배치 실패", e);
            meterRegistry.counter("checkpoint.scheduler.failure", "type", "sync").increment();
        } finally {
            sample.stop(meterRegistry.timer("checkpoint.scheduler.duration", "type", "sync"));
        }
    }

    @Scheduled(fixedDelay = 300_000)
    public void reconcileCheckpoints() {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            chatRoomSyncService.reconcileFromDb();
            meterRegistry.counter("checkpoint.scheduler.success", "type", "reconcile").increment();
        } catch (Exception e) {
            log.error("checkpoint 정합성 보정 배치 실패", e);
            meterRegistry.counter("checkpoint.scheduler.failure", "type", "reconcile").increment();
        } finally {
            sample.stop(meterRegistry.timer("checkpoint.scheduler.duration", "type", "reconcile"));
        }
    }
}