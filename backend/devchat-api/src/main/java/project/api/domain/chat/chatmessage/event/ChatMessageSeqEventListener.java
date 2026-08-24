package project.api.domain.chat.chatmessage.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import project.api.domain.chat.chatroom.app.ChatRoomSequenceService;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatMessageSeqEventListener {

    private final ChatRoomSequenceService chatRoomSequenceService;

    @Async("chatSeqExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void incrementUnreadCache(ChatMessageSavedEvent event) {
        chatRoomSequenceService.incrementCache(event.roomId());
    }
}
