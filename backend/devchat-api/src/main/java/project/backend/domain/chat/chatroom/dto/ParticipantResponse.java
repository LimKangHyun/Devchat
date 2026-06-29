package project.backend.domain.chat.chatroom.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ParticipantResponse {

	private String nickname;
	private boolean owner;
}
