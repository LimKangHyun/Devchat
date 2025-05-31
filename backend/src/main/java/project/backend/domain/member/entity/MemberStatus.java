package project.backend.domain.member.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import project.backend.domain.chat.chatroom.entity.ChatRoom;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberStatus {

	@Id
	private Long id;

	@OneToOne
	@MapsId
	@JoinColumn(name = "member_id")
	private Member member;

	@OneToOne
	@JoinColumn(name = "room_id")
	@Setter
	private ChatRoom room;

	public MemberStatus(Member member) {
		this.member = member;
	}
}
