package project.api.domain.member.friend.entity;

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
import project.api.domain.member.entity.Member;

@Entity
@Getter
@NoArgsConstructor
@Table(
	name = "friends",
	indexes = @Index(name = "idx_owner_friend", columnList = "owner_id, friend_id"),
	uniqueConstraints = @UniqueConstraint(columnNames = {"owner_id", "friend_id"})
)
public class Friends {

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
	public Friends(Member owner, Member friend) {
		this.owner = owner;
		this.friend = friend;
		this.createdAt = LocalDateTime.now();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof Friends)) {
			return false;
		}
		Friends other = (Friends) o;
		return id != null && id.equals(other.id);
	}

	@Override
	public int hashCode() {
		return getClass().hashCode();
	}
}