package project.backend.domain.chat.chatroom.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomInfoResponse {

	private Long roomId;

	private String roomName;

	private String repositoryUrl;

	private String inviteCode;

}




