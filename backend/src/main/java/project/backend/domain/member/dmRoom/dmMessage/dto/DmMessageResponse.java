package project.backend.domain.member.dmRoom.dmMessage.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import project.backend.domain.chat.chatmessage.entity.MessageStatus;
import project.backend.domain.chat.chatmessage.entity.MessageType;
import project.backend.domain.member.dmRoom.dmMessage.entity.DmMessage;

public record DmMessageResponse(
	Long roomId,
	Long senderId,
	String content,
	String senderNickName,
	MessageType type,
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
