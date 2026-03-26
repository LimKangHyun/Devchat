package project.backend.domain.chat.chatmessage.dto.event;

import project.backend.auth.dto.MemberDetails;
import project.backend.domain.chat.chatmessage.dto.ChatMessageResponse;
import project.backend.domain.chat.chatmessage.entity.ChatMessage;

public record ChatMessageBroadcastEvent(
    Long roomId,
    ChatMessageResponse response
) {
    public static ChatMessageBroadcastEvent from(ChatMessage message, MemberDetails sender, String profileImage) {
        ChatMessageResponse response = ChatMessageResponse.builder()
                .senderName(sender.getNickname())
                .content(message.getContent())
                .type(message.getType())
                .sendAt(message.getSendAt())
                .language(message.getCodeLanguage())
                .profileImageUrl(profileImage)
                .chatImageUrl(
                        message.getChatImage() != null ? message.getChatImage().getStoreFileName() : null
                )
                .senderId(sender.getId())
                .messageId(message.getId())
                .status(message.getStatus())
                .sequence(message.getSequence())
                .build();

        return new ChatMessageBroadcastEvent(message.getChatRoom().getId(), response);
    }
}
