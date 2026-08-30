package project.api.domain.chat.chatroom.app;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatRoomCheckpointScheduler {

    private final ChatRoomSyncService chatRoomSyncService;

    @Scheduled(fixedDelay = 30_000)
    public void syncCheckpoints() {
        try {
            chatRoomSyncService.syncToDb();
        } catch (Exception e) {
            log.error("checkpoint 동기화 배치 실패", e);
        }
    }

    @Scheduled(fixedDelay = 300_000)
    public void reconcileCheckpoints() {
        try {
            chatRoomSyncService.reconcileFromDb();
        } catch (Exception e) {
            log.error("checkpoint 정합성 보정 배치 실패", e);
        }
    }
}