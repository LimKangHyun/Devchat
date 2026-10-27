package project.api.domain.chat.chatmessage.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import project.api.domain.notification.app.MentionNotificationService;

/** 메시지 저장이 커밋된 뒤 비동기로 멘션 알림을 만든다. 채팅 전송 속도에 영향을 주지 않게 분리했다 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatMentionListener {

    private final MentionNotificationService mentionNotificationService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ChatMentionEvent event) {
        try {
            mentionNotificationService.notifyMentions(event);
        } catch (Exception e) {
            log.error("멘션 알림 실패. roomId={}, senderId={}", event.roomId(), event.senderId(), e);
        }
    }
}
