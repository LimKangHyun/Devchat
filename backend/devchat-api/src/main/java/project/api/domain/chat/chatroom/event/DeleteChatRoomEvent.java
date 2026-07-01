package project.api.domain.chat.chatroom.event;

public record DeleteChatRoomEvent(
	Long roomId,
	String roomName,
	String repositoryUrl,
	Long webhookId,
	Long memberId
) { }