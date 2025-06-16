package project.backend.domain.member.notification.dto;

import project.backend.domain.member.friend.dto.event.FriendEvent;
import project.backend.domain.member.notification.entity.Notification;
import project.backend.domain.member.notification.entity.NotificationType;

public record AlertTemplate(
	NotificationType type,
	String senderUsername,
	String senderNickname,
	String content,
	String senderImg,
	Long referenceId
) {

	public static AlertTemplate ofFriendRequestEvent(FriendEvent friendEvent) {
		return new AlertTemplate(
			NotificationType.FRIEND_REQUESTED,
			friendEvent.senderUsername(),
			friendEvent.senderNickname(),
			getContentByType(friendEvent.senderNickname(), NotificationType.FRIEND_REQUESTED),
			friendEvent.senderImg(),
			friendEvent.referenceId()
		);
	}

	public static AlertTemplate ofFriendAcceptEvent(FriendEvent friendEvent) {
		return new AlertTemplate(
			NotificationType.FRIEND_ACCEPTED,
			friendEvent.senderUsername(),
			friendEvent.senderNickname(),
			getContentByType(friendEvent.senderNickname(), NotificationType.FRIEND_ACCEPTED),
			friendEvent.senderImg(),
			friendEvent.referenceId()
		);
	}

	public static AlertTemplate ofNotification(Notification notification) {
		return new AlertTemplate(
			notification.getType(),
			notification.getSender().getUsername(),
			notification.getSender().getNickname(),
			getContentByType(notification.getSender().getNickname(), notification.getType()),
			notification.getSender().getProfileImage(),
			notification.getReferenceId()
		);
	}

	private static String getContentByType(String senderNickname, NotificationType type) {
		return switch (type) {
			case FRIEND_REQUESTED -> senderNickname + "님이 친구요청을 보냈습니다.";
			case FRIEND_ACCEPTED -> senderNickname + "님이 친구요청을 수락했습니다.";
			case CODE_REVIEW -> senderNickname + "님이 코드 리뷰를 추가했습니다.";
		};
	}
}
