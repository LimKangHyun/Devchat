package project.api.domain.chat.chatmessage.event;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import project.api.domain.chat.chatmessage.entity.MessageType;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EventMessageResponse {

	private Long messageId;

	private MessageType type;

	private String sender;

	private Long roomId;

	private String content;

	private LocalDateTime createdAt;

}

