package project.api.domain.chat.chatroom.app;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatRoomSequenceScheduler {

    private final ChatRoomSyncService chatRoomSyncService;

    @Scheduled(fixedRate = 60000)
    public void syncLastSequence() {
        try {
            chatRoomSyncService.syncToDb();
            log.info("last_sequence 동기화 배치 완료");
        } catch (Exception e) {
            log.error("시퀀스 동기화 배치 중 에러 발생: {}", e.getMessage());
        }
    }
}