package project.backend.domain.chat.chatmessage.dto.event;

import project.backend.domain.chat.chatmessage.entity.ChatMessage;

public record ChatMessageSavedEvent(
    Long messageId,
    Long roomId,
    String content
) {

    public static ChatMessageSavedEvent from(ChatMessage message) {
        return new ChatMessageSavedEvent(
            message.getId(),
            message.getChatRoomId(),
            message.getContent()
        );
    }
}