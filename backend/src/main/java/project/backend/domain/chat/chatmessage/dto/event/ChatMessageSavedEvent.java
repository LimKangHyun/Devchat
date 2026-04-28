package project.backend.domain.chat.chatmessage.dto.event;

import project.backend.domain.chat.chatmessage.entity.ChatMessage;

public record ChatMessageSavedEvent(
        Long roomId
) {

    public static ChatMessageSavedEvent from(ChatMessage message) {
        return new ChatMessageSavedEvent(
            message.getChatRoomId()
        );
    }
}