package project.backend.domain.member.friend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import project.backend.domain.member.entity.Member;

@Entity
@Getter
@NoArgsConstructor
@Table(
	name = "friends_list",
	indexes = @Index(name = "idx_owner_friend", columnList = "owner_id, friend_id"),
	uniqueConstraints = @UniqueConstraint(columnNames = {"owner_id", "friend_id"})
)
public class FriendsList {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "owner_id")
	private Member owner;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "friend_id")
	private Member friend;

	@Column(nullable = false)
	private LocalDateTime createdAt;

	@Builder
	public FriendsList(Member owner, Member friend) {
		this.owner = owner;
		this.friend = friend;
		this.createdAt = LocalDateTime.now();
	}
}