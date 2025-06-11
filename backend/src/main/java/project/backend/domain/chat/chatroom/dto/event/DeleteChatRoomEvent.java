package project.backend.domain.chat.chatroom.dto.event;

public record DeleteChatRoomEvent(
	Long roomId,
	String roomName
) {

}
