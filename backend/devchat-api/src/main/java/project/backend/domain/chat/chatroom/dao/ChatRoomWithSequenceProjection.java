package project.backend.domain.chat.chatroom.dao;

import project.common.enums.IndexingStatus;

public interface ChatRoomWithSequenceProjection {

    Long getChatRoomId();

    Long getLastReadSequence();

    String getName();

    String getInviteCode();

    String getRepositoryUrl();

    IndexingStatus getIndexingStatus();

}