package project.api.domain.chat.chatmessage.event;

import project.api.auth.dto.MemberDetails;
import project.api.domain.chat.chatmessage.entity.ChatMessage;

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
