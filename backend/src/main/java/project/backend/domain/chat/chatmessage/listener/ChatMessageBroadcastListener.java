package project.backend.domain.chat.chatmessage.listener;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import project.backend.domain.chat.chatmessage.dto.ChatMessageResponse;
import project.backend.domain.chat.chatmessage.dto.event.ChatMessageBroadcastEvent;
import project.backend.domain.member.app.ProfileImageCache;

@Component
@RequiredArgsConstructor
public class ChatMessageBroadcastListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final ProfileImageCache profileImageCache;

    @Async("chatBroadcastExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleBroadcast(ChatMessageBroadcastEvent event) {
        String profileImage = profileImageCache.getProfileImage(event.senderId());
        ChatMessageResponse response = ChatMessageResponse.builder()
                .senderName(event.senderNickname())
                .senderId(event.senderId())
                .profileImageUrl(profileImage)
                .content(event.message().getContent())
                .type(event.message().getType())
                .createdAt(event.message().getCreatedAt())
                .language(event.message().getCodeLanguage())
                .chatImageUrl(event.message().getChatImage() != null
                        ? event.message().getChatImage().getStoreFileName() : null)
                .messageId(event.message().getId())
                .status(event.message().getStatus())
                .build();
        messagingTemplate.convertAndSend("/topic/chat/" + event.roomId(), response);
    }
}