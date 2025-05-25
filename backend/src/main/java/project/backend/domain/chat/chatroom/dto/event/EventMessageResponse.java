package project.backend.domain.chat.chatroom.dto.event;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import project.backend.domain.chat.chatmessage.entity.MessageType;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EventMessageResponse {

	private MessageType type;

	private String sender;

	private Long roomId;

	private String content;

	private LocalDateTime sendAt;

}

