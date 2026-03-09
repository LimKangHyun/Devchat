package project.backend.domain.chat.chatmessage.dto.event;

import project.backend.domain.chat.chatmessage.dto.ChatMessageResponse;

public record ChatMessageBroadcastEvent(
    Long roomId,
    ChatMessageResponse response
) {

}
