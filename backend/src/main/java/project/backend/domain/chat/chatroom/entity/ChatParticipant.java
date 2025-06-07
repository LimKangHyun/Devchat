package project.backend.domain.chat.chatroom.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import project.backend.domain.member.entity.Member;


@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class ChatParticipant {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "chat_participant_id")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "member_id")
	private Member participant;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "room_id")
	private ChatRoom chatRoom;

	private boolean isOwner;

	@Builder
	public ChatParticipant(Long id, Member participant, ChatRoom chatRoom, boolean isOwner) {
		this.id = id;
		this.participant = participant;
		this.chatRoom = chatRoom;
		this.isOwner = isOwner;
	}

	public static ChatParticipant of(Member participant, ChatRoom chatRoom) {
		return ChatParticipant.builder()
			.participant(participant)
			.chatRoom(chatRoom)
			.build();
	}

	public static ChatParticipant createOwner(Member participant, ChatRoom chatRoom) {
		return ChatParticipant.builder()
			.participant(participant)
			.chatRoom(chatRoom)
			.isOwner(true)
			.build();
	}

}

