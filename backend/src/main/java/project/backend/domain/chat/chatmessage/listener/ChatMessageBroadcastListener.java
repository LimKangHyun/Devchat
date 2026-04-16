package project.backend.domain.chat.chatmessage.listener;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import project.backend.domain.chat.chatmessage.app.event.ChatMessageBroadcastEvent;

@Component
@RequiredArgsConstructor
public class ChatMessageBroadcastListener {

    private final SimpMessagingTemplate messagingTemplate;

    @Async("chatBroadcastExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleBroadcast(ChatMessageBroadcastEvent event) {
        messagingTemplate.convertAndSend("/topic/chat/" + event.roomId(), event.response());
    }

}
