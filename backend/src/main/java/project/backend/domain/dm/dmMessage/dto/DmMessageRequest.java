package project.backend.domain.dm.dmMessage.dto;

import project.backend.domain.chat.chatmessage.entity.MessageType;

public record DmMessageRequest(
	String receiverUsername,
	String content,
	MessageType type
) {

}
