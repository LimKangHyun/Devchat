package project.backend.domain.member.friend.entity;

import jakarta.persistence.Column;
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
public class FriendRequest {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	private Member sender;

	@ManyToOne(fetch = FetchType.LAZY)
	private Member receiver;

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

}

