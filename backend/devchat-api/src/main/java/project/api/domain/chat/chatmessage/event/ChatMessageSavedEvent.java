package project.api.domain.chat.chatmessage.event;

import project.api.domain.chat.chatmessage.entity.ChatMessage;

public record ChatMessageSavedEvent(
        Long roomId
) {

    public static ChatMessageSavedEvent from(ChatMessage message) {
        return new ChatMessageSavedEvent(
            message.getChatRoomId()
        );
    }
}