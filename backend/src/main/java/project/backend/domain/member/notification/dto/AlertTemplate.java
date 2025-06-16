package project.backend.domain.member.notification.dto;

import project.backend.domain.member.friend.dto.event.FriendRequestEvent;
import project.backend.domain.member.notification.entity.NotificationType;

public record AlertTemplate(
	NotificationType type,
	String sender,
	String senderImg,
	Long referenceId
) {

	public static AlertTemplate of(FriendRequestEvent friendRequestEvent) {
		return new AlertTemplate(
			NotificationType.FRIEND_REQUESTED,
			friendRequestEvent.senderUsername(),
			friendRequestEvent.senderProfileImg(),
			friendRequestEvent.senderId()
		);
	}
}
