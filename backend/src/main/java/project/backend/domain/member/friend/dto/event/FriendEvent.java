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

	// 기본 메시지를 사용하는 팩토리
	public static FriendEvent create(
		NotificationType type,
		Member sender,
		Member receiver,
		Long referenceId,
		LocalDateTime time
	) {
		return new FriendEvent(
			type,
			receiver.getUsername(),
			sender.getUsername(),
			sender.getNickname(),
			sender.getProfileImage(),
			getContentByType(sender.getNickname(), type),
			referenceId,
			time
		);
	}

	// 커스텀 메시지를 사용하는 팩토리
	public static FriendEvent create(
		NotificationType type,
		Member sender,
		Member receiver,
		String content,
		Long referenceId,
		LocalDateTime time
	) {
		return new FriendEvent(
			type,
			receiver.getUsername(),
			sender.getUsername(),
			sender.getNickname(),
			sender.getProfileImage(),
			content,
			referenceId,
			time
		);
	}

	// 요청
	public static FriendEvent ofFriendRequest(Member sender, Member receiver) {
		return create(NotificationType.FRIEND_REQUESTED, sender, receiver, sender.getId(),
			LocalDateTime.now());
	}

	// 수락 (상대방에게 알림)
	public static FriendEvent ofFriendAcceptEvent(Member acceptor, Member requester) {
		return create(NotificationType.FRIEND_ACCEPTED, acceptor, requester, acceptor.getId(),
			LocalDateTime.now());
	}

	// 거절 (상대방에게 알림)
	public static FriendEvent ofFriendRejectEvent(Member rejecter, Member requester) {
		return create(NotificationType.FRIEND_REJECTED, rejecter, requester, rejecter.getId(),
			LocalDateTime.now());
	}

	// 수락 (본인에게 알림) - 커스텀 메시지 사용
	public static FriendEvent ofFriendAcceptSelf(Member acceptor, Member requester) {
		String content = requester.getNickname() + "님과 친구가 되었습니다.";
		return create(
			NotificationType.FRIEND_ACCEPTED,
			requester,
			acceptor,
			content,
			requester.getId(),
			LocalDateTime.now()
		);
	}

	// Notification 객체로부터 생성
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

	// 콘텐츠 메시지 생성
	private static String getContentByType(String senderNickname, NotificationType type) {
		return switch (type) {
			case FRIEND_REQUESTED -> senderNickname + "님이 친구요청을 보냈습니다.";
			case FRIEND_ACCEPTED -> senderNickname + "님이 친구요청을 수락했습니다.";
			case FRIEND_REJECTED -> senderNickname + "님이 친구요청을 거절했습니다.";
			case CODE_REVIEW -> senderNickname + "님이 코드 리뷰를 추가했습니다.";
		};
	}
}
