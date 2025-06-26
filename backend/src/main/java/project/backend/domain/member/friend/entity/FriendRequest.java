package project.backend.domain.member.friend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import project.backend.domain.member.entity.Member;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
	name = "friend_request",
	uniqueConstraints = @UniqueConstraint(columnNames = {"receiver_id", "sender_id"})
)
public class FriendRequest {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "receiver_id")
	private Member receiver;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "sender_id")
	private Member sender;

	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private RequestStatus status = RequestStatus.PENDING;

	@Column(nullable = false)
	private LocalDateTime requestedAt = LocalDateTime.now();

	private LocalDateTime responseAt;

	public void accept() {
		this.status = RequestStatus.ACCEPTED;
		this.responseAt = LocalDateTime.now();
	}

	public void reject() {
		this.status = RequestStatus.REJECTED;
		this.responseAt = LocalDateTime.now();
	}

	public FriendRequest(Member receiver, Member sender) {
		this.receiver = receiver;
		this.sender = sender;
		this.status = RequestStatus.PENDING;
		this.requestedAt = LocalDateTime.now();
	}

}

