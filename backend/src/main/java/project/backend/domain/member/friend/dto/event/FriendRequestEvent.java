package project.backend.domain.member.friend.dto.event;

import java.time.LocalDateTime;
import project.backend.domain.member.entity.Member;

public record FriendRequestEvent(
	String senderUsername,
	String receiverUsername,
	String senderProfileImg,
	Long senderId,
	LocalDateTime createdAt
) {

	public static FriendRequestEvent from(Member sender, Member receiver) {
		return new FriendRequestEvent(
			sender.getUsername(),
			receiver.getUsername(),
			sender.getProfileImage(),
			sender.getId(),
			LocalDateTime.now()
		);
	}
}
