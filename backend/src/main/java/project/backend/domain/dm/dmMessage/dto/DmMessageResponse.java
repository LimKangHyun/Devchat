package project.backend.domain.dm.dmMessage.dto;

import java.time.LocalDateTime;
import project.backend.domain.dm.dmMessage.DmMessageType;
import project.backend.domain.dm.dmMessage.entity.DmMessage;

public record DmMessageResponse(
	Long roomId,
	Long senderId,
	String content,
	String senderNickName,
	DmMessageType type,
	Long messageId,
	LocalDateTime sendAt
) {

	public static DmMessageResponse from(DmMessage message) {
		return new DmMessageResponse(
			message.getRoom().getId(),
			message.getSender().getId(),
			message.getContent(),
			message.getSender().getNickname(),
			message.getType(),
			message.getId(),
			message.getSentAt()
		);
	}
}
