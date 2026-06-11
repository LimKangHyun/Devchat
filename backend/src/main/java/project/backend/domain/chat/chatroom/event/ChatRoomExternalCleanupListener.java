package project.backend.domain.chat.chatroom.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import project.backend.domain.aireview.app.RepoIndexingService;
import project.backend.domain.aireview.client.PineconeClient;
import project.backend.domain.github.app.GitMessageService;

import static org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT;

@Slf4j
@Component
@RequiredArgsConstructor
class ChatRoomExternalCleanupListener {

    private final PineconeClient pineconeClient;
    private final GitMessageService gitMessageService;
    private final RepoIndexingService repoIndexingService;

    @Async("chatRoomEventExecutor")
    @TransactionalEventListener(phase = AFTER_COMMIT)
    public void handleRoomDelete(DeleteChatRoomEvent event) {
        if (event.repositoryUrl().isBlank()) return;
        repoIndexingService.cancelIndexing(event.roomId());
        repoIndexingService.waitUntilIndexingDone(event.roomId());

        try {
            pineconeClient.deleteNamespace(String.valueOf(event.roomId()));
        } catch (Exception e) {
            log.warn("Pinecone namespace 삭제 실패. roomId={}", event.roomId(), e);
        }

        try {
            gitMessageService.deleteWebhook(event.repositoryUrl(), event.webhookId(), event.memberId());
        } catch (Exception e) {
            log.warn("webhook 삭제 실패. roomId={}", event.roomId(), e);
        }
    }
}