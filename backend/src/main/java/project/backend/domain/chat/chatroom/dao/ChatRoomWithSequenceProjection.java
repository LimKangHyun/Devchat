package project.backend.domain.chat.chatroom.dao;

public interface ChatRoomWithSequenceProjection {

    Long getChatRoomId();

    Long getLastReadSequence();

    String getName();

    String getInviteCode();

    String getRepositoryUrl();

}