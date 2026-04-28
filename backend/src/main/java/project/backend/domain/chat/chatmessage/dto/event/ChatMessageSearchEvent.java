package project.backend.domain.chat.chatmessage.dto.event;

import project.backend.domain.chat.chatmessage.entity.ChatMessage;

public record ChatMessageSearchEvent(
        Long messageId,
        Long roomId,
        String content
) {
    public static ChatMessageSearchEvent from(ChatMessage message) {
        return new ChatMessageSearchEvent(
                message.getId(),
                message.getChatRoomId(),
                message.getContent()
        );
    }
}