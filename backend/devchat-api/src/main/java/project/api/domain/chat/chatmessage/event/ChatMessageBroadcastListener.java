package project.api.domain.chat.chatmessage.event;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import project.api.domain.chat.chatmessage.dto.ChatMessageResponse;
import project.api.domain.chat.chatmessage.mapper.ChatMessageMapper;
import project.api.domain.member.app.ProfileImageCache;

@Component
@RequiredArgsConstructor
public class ChatMessageBroadcastListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final ProfileImageCache profileImageCache;
    private final ChatMessageMapper messageMapper;

    @Async("chatBroadcastExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleBroadcast(ChatMessageBroadcastEvent event) {
        String profileImage = profileImageCache.getProfileImage(event.senderId());
        ChatMessageResponse response = messageMapper.toBroadcastResponse(event, profileImage);
        messagingTemplate.convertAndSend("/topic/chat/" + event.roomId(), response);
    }

    public static final String ACK_DESTINATION = "/queue/message-ack";

    // 다시 보낸 메시지가 이미 저장돼 있으면 보낸 사람에게만 저장된 메시지를 돌려준다
    @Async("chatBroadcastExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleAck(ChatMessageAckEvent event) {
        messagingTemplate.convertAndSendToUser(event.senderUsername(), ACK_DESTINATION, event.message());
    }
}