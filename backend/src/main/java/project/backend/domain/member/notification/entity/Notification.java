package project.backend.domain.member.notification.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import project.backend.domain.member.entity.Member;
import project.backend.domain.member.friend.entity.FriendRequest;

@Entity
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "receiver_member_id")
	private Member receiver;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "sender_member_id")
	private Member sender;

	@Enumerated(EnumType.STRING)
	private NotificationType type;

	//알림이 온 도메인 id (채팅방 알림이면 해당 채팅방으로 이동해야함)
	private Long referenceId;

	private boolean isRead = false;

	private LocalDateTime createdAt;

	public void markAsRead() {
		this.isRead = true;
	}

	public static Notification of(FriendRequest friendRequest) {
		return Notification.builder()
			.receiver(friendRequest.getReceiver())
			.sender(friendRequest.getSender())
			.type(NotificationType.FRIEND_REQUESTED)
			.createdAt(LocalDateTime.now())
			.build();
	}
}
