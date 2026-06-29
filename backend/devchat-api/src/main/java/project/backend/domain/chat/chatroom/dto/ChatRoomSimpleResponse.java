package project.backend.domain.chat.chatroom.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
//임창인
public class ChatRoomSimpleResponse {

	private Long id;
	private String name;
	private String repositoryUrl;
	private Long ownerId;
	private String inviteCode;

	public static ChatRoomSimpleResponse of(Long id, String name, String repositoryUrl, Long ownerId, String inviteCode) {
		return new ChatRoomSimpleResponse(id, name, repositoryUrl, ownerId, inviteCode);
	}
}
