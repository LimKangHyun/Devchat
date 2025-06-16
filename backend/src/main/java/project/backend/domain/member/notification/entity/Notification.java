package project.backend.domain.member.notification.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import project.backend.domain.member.entity.Member;

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
	private Member receiver;

	@Enumerated(EnumType.STRING)
	private NotificationType type;

	//알림이 온 도메인 id (채팅방 알림이면 해당 채팅방으로 이동해야함)
	private Long referenceId;

	private String senderName;

	private String content;

	private boolean read = false;

	private LocalDateTime createdAt;

	public void markAsRead() {
		this.read = true;
	}
}
