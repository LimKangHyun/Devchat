package project.backend.domain.member.friend.dto.event;

import java.time.LocalDateTime;
import project.backend.domain.member.entity.Member;
import project.backend.domain.member.notification.entity.NotificationType;

public record FriendEvent(
	NotificationType type,
	String senderUsername,
	String senderNickname,
	String receiverUsername,
	String senderProfileImg,
	Long senderId,
	LocalDateTime createdAt
) {

	public static FriendEvent ofFriendRequest(Member sender, Member receiver) {
		return new FriendEvent(
			NotificationType.FRIEND_REQUESTED,
			sender.getUsername(),
			sender.getNickname(),
			receiver.getUsername(),
			sender.getProfileImage(),
			sender.getId(),
			LocalDateTime.now()
		);
	}
}
