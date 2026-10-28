package project.api.domain.chat.chatmessage.dto;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;
import project.api.domain.chat.chatmessage.entity.MessageStatus;
import project.api.domain.chat.chatmessage.entity.MessageType;

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

	private Long aiReviewId;
	private Integer prNumber;
	private String prStatus;
	private String aiReviewStatus;
	private String publishedBy;
	/** 보낸 브라우저가 붙인 임시 id. 화면의 "전송 중" 메시지를 이 메시지로 바꾸는 데 쓴다 */
	private String clientMessageId;
	/** 답장한 원문 미리보기 (답장이 아니면 null) */
	private ReplyPreview replyTo;
}