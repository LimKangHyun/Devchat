package project.backend.domain.chat.chatmessage.dto.event;

import project.backend.auth.dto.MemberDetails;
import project.backend.domain.chat.chatmessage.entity.ChatMessage;

public record ChatMessageBroadcastEvent(
        Long roomId,
        Long senderId,
        String senderNickname,
        ChatMessage message
) {
    public static ChatMessageBroadcastEvent from(ChatMessage message, MemberDetails sender) {
        return new ChatMessageBroadcastEvent(
                message.getChatRoom().getId(),
                sender.getId(),
                sender.getNickname(),
                message
        );
    }
}
