package project.backend.domain.chat.chatmessage.dto;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;
import project.backend.domain.chat.chatmessage.entity.MessageStatus;
import project.backend.domain.chat.chatmessage.entity.MessageType;

@Getter
@Builder
public class ChatMessageResponse {

	private String content;
	private String senderName;
	private LocalDateTime createdAt;
	private MessageType type;
	private String language;
	private String profileImageUrl;
	private String chatImageUrl;
	private Long senderId;
	private Long messageId;
	private MessageStatus status;
	private boolean githubPublished;

	private Integer prNumber;
	private String filePath;
	private String inlineReviews; // JSON 문자열
}