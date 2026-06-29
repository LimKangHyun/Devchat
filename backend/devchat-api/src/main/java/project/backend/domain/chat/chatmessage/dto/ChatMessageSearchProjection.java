package project.backend.domain.chat.chatmessage.dto;

public interface ChatMessageSearchProjection {
    Long getId();
    String getContent();
    Long getChatRoomId();
}
