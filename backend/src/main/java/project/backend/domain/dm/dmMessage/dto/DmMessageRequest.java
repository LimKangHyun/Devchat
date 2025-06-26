package project.backend.domain.dm.dmMessage.dto;

import project.backend.domain.chat.chatmessage.entity.MessageType;
import project.backend.domain.dm.dmMessage.DmMessageType;

public record DmMessageRequest(
	String receiverUsername,
	String content,
	DmMessageType type
) {

}
