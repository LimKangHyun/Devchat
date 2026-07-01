package project.api.domain.chat.chatmessage.dto;

public interface ChatMessageSearchProjection {
    Long getId();
    String getContent();
    Long getChatRoomId();
}
