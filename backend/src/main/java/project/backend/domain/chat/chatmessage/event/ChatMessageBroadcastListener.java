package project.backend.domain.chat.chatmessage.event;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import project.backend.domain.chat.chatmessage.dto.ChatMessageResponse;
import project.backend.domain.chat.chatmessage.mapper.ChatMessageMapper;
import project.backend.domain.member.app.ProfileImageCache;

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
}