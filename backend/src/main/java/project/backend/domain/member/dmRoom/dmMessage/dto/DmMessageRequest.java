package project.backend.domain.member.dmRoom.dmMessage.dto;

import project.backend.domain.chat.chatmessage.entity.MessageType;

public record DmMessageRequest(
	String receiverUsername,
	String content,
	MessageType type
) {

}
