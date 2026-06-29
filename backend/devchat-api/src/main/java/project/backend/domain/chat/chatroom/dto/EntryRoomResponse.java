package project.backend.domain.chat.chatroom.dto;

import project.common.enums.IndexingStatus;

public record EntryRoomResponse(
	Long roomId,
	String roomName,
	Long ownerId,
	Boolean alarmEnabled,
	String repositoryUrl,
	boolean aiReviewEnabled,
	boolean aiSummaryEnabled,
	IndexingStatus indexingStatus
) {

}