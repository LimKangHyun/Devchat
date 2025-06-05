package project.backend.domain.chat.chatmessage.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import project.backend.domain.chat.chatroom.entity.ChatRoom;
import project.backend.domain.imagefile.ImageFile;
import project.backend.domain.member.entity.Member;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ChatMessage {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "message_id")
	private Long id;

	@ManyToOne
	@JoinColumn(name = "member_id")
	private Member sender;

	@ManyToOne
	@JoinColumn(name = "room_id")
	private ChatRoom chatRoom;

	@Lob
	@Column(columnDefinition = "TEXT")
	private String content;

	private LocalDateTime sendAt;

	@Enumerated(EnumType.STRING)
	private MessageType type;

	private String codeLanguage; //추가, 문법마다 다르게 하이라이팅을 하기 위함

	@OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
	@JoinColumn(name = "chat_image_id")
	private ImageFile chatImage;

	@Builder.Default
	@Enumerated(EnumType.STRING)
	private MessageStatus status = MessageStatus.NO_CHANGE;

	public void updateContent(String newContent) {
		if (newContent != null) {
			content = newContent;
			status = MessageStatus.EDITED;
		}
	}

	public void updateLanguage(String language) {
		if (language != null) {
			codeLanguage = language;
			status = MessageStatus.EDITED;
		}
	}

	public void delete() {
		status = MessageStatus.DELETED;
	}
}
