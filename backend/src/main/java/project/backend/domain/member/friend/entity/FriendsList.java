package project.backend.domain.member.friend.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
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
public class FriendsList {

	@Id
	@GeneratedValue
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	private Member owner;

	@ManyToOne(fetch = FetchType.LAZY)
	private Member friend;

	private LocalDateTime createdAt;
}