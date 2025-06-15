package project.backend.domain.chat.codereview.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import project.backend.domain.chat.chatmessage.entity.ChatMessage;
import project.backend.domain.member.entity.Member;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Getter
public class CodeReview {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "review_id")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@Column(name = "message_id")
	private ChatMessage message;

	@ManyToOne(fetch = FetchType.LAZY)
	@Column(name = "member_id")
	private Member author;

	private int lineNumber;

	@Column(columnDefinition = "TEXT")
	private String content;

	@CreationTimestamp
	private LocalDateTime createdAt;

	public void updateContent(String content) {
		this.content = content;
	}

}
