package project.api.domain.dm.dmMessage.entity;

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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import project.api.domain.dm.dmMessage.DmMessageType;
import project.api.domain.dm.dmRoom.entity.DmRoom;
import project.api.domain.member.entity.Member;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DmMessage {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "room_id", nullable = false)
	private DmRoom room;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "sender_id", nullable = false)
	private Member sender;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String content;

	@Enumerated(EnumType.STRING)
	private DmMessageType type;

	@Column(name = "sent_at", nullable = false, updatable = false)
	private LocalDateTime sentAt = LocalDateTime.now();

	public DmMessage(DmRoom room, Member sender, String content,
		DmMessageType type) {
		this.room = room;
		this.sender = sender;
		this.content = content;
		this.type = type;
	}

}