package project.backend.domain.chat.chatroom.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import project.backend.domain.chat.chatmessage.entity.ChatMessage;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public class ChatRoom {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "room_id")
	private Long id;

	@Column(nullable = false)
	private String name;

	private LocalDateTime createdAt;

	private String repositoryUrl;

	private String inviteCode;

	@OneToMany(mappedBy = "chatRoom", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<ChatMessage> messages = new ArrayList<>();

	@OneToMany(mappedBy = "chatRoom", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<ChatParticipant> participants = new ArrayList<>();

	@Builder
	public ChatRoom(String name, LocalDateTime createdAt, String repositoryUrl, String inviteCode,
		List<ChatMessage> messages, List<ChatParticipant> participants) {
		this.name = name;
		this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
		this.repositoryUrl = repositoryUrl;
		this.inviteCode = inviteCode;
		if (messages != null) {
			this.messages = messages;
		}
		if (participants != null) {
			this.participants = participants;
		}
	}

	public void addParticipant(ChatParticipant chatParticipant) {
		participants.add(chatParticipant);
	}
}
