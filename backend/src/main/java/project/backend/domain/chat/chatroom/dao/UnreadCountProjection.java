package project.backend.domain.chat.chatroom.dao;

public interface UnreadCountProjection {

    Long getChatRoomId();

    Long getLastReadMessageId();
}