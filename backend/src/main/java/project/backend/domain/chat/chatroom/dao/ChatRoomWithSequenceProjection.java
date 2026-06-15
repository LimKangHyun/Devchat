package project.backend.domain.chat.chatroom.dao;

import project.backend.domain.chat.chatroom.entity.IndexingStatus;

public interface ChatRoomWithSequenceProjection {

    Long getChatRoomId();

    Long getLastReadSequence();

    String getName();

    String getInviteCode();

    String getRepositoryUrl();

    IndexingStatus getIndexingStatus();
}