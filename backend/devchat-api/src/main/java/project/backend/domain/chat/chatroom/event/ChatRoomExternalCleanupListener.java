package project.backend.domain.chat.chatroom.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import project.backend.domain.github.app.GitMessageService;
import project.backend.global.redis.RedisStreamClient;

import static org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT;

@Slf4j
@Component
@RequiredArgsConstructor
class ChatRoomExternalCleanupListener {

    private final GitMessageService gitMessageService;
    private final RedisStreamClient redisStreamClient;

    @Async("chatRoomEventExecutor")
    @TransactionalEventListener(phase = AFTER_COMMIT)
    public void handleRoomDelete(DeleteChatRoomEvent event) {
        try {
            gitMessageService.deleteWebhook(event.repositoryUrl(), event.webhookId(), event.memberId());
        } catch (Exception e) {
            log.warn("webhook 삭제 실패. roomId={}", event.roomId(), e);
        }

        if (event.repositoryUrl() == null || event.repositoryUrl().isBlank()) return;

        try {
            redisStreamClient.publishDeleteIndex(event.roomId());
        } catch (Exception e) {
            log.warn("Pinecone 삭제 Stream 발행 실패. roomId={}", event.roomId(), e);
        }
    }
}