package project.backend.domain.chat.chatroom.dto;

import project.backend.domain.chat.chatroom.entity.IndexingStatus;

public record EntryRoomResponse(
	Long roomId,
	String roomName,
	Long ownerId,
	Boolean alarmEnabled,
	String repositoryUrl,
	boolean aiReviewEnabled,
	IndexingStatus indexingStatus
) {

}
