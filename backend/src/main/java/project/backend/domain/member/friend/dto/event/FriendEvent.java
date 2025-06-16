package project.backend.domain.member.friend.dto.event;

import java.time.LocalDateTime;
import project.backend.domain.member.entity.Member;
import project.backend.domain.member.notification.entity.Notification;
import project.backend.domain.member.notification.entity.NotificationType;

public record FriendEvent(
	NotificationType type,
	String receiverUsername,
	String senderUsername,
	String senderNickname,
	String senderImg,
	String content,
	Long referenceId,
	LocalDateTime createdAt
) {

	public static FriendEvent ofFriendRequest(Member sender, Member receiver) {
		return new FriendEvent(
			NotificationType.FRIEND_REQUESTED,
			receiver.getUsername(),
			sender.getUsername(),
			sender.getNickname(),
			sender.getProfileImage(),
			getContentByType(sender.getNickname(), NotificationType.FRIEND_REQUESTED),
			sender.getId(),
			LocalDateTime.now()
		);
	}

	public static FriendEvent ofFriendAcceptEvent(Member acceptor, Member requester) {
		return new FriendEvent(
			NotificationType.FRIEND_ACCEPTED,
			requester.getUsername(),
			acceptor.getUsername(),
			acceptor.getNickname(),
			acceptor.getProfileImage(),
			getContentByType(acceptor.getNickname(), NotificationType.FRIEND_ACCEPTED),
			acceptor.getId(),
			LocalDateTime.now()
		);
	}

	public static FriendEvent ofNotification(Notification notification) {
		return new FriendEvent(
			notification.getType(),
			notification.getReceiver().getUsername(),
			notification.getSender().getUsername(),
			notification.getSender().getNickname(),
			notification.getSender().getProfileImage(),
			getContentByType(notification.getSender().getNickname(), notification.getType()),
			notification.getReferenceId(),
			LocalDateTime.now()
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
