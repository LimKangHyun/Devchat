package project.backend.domain.chat.chatroom.dto;

public record EntryRoomResponse(
	Long roomId,
	String roomName,
	Long ownerId,
	Boolean alarmEnabled,
	String repositoryUrl,
	boolean aiReviewEnabled
) {

}
