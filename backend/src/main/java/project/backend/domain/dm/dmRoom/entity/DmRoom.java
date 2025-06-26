package project.backend.domain.dm.dmRoom.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import project.backend.domain.member.entity.Member;

@Getter
@Entity
@Table(name = "dm_room", uniqueConstraints = {
	@UniqueConstraint(name = "uq_dm_users", columnNames = {"member1_id", "member2_id"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DmRoom {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "member1_id", nullable = false)
	private Member member1;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "member2_id", nullable = false)
	private Member member2;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt = LocalDateTime.now();

	public DmRoom(Member member1, Member member2) {
		if (member1.getId() < member2.getId()) {
			this.member1 = member1;
			this.member2 = member2;
		} else {
			this.member1 = member2;
			this.member2 = member1;
		}
	}
}