package project.backend.domain.chat.chatroom.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AllRoomsResponse {

	private Long roomId;
	private boolean alarmEnabled;

}
