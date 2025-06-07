package project.backend.domain.chat.chatmessage.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ChatScrollResponse {

	private List<ChatMessageResponse> messages;
	private Long nextCursor;
}
