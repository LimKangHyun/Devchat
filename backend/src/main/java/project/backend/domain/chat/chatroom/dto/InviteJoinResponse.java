package project.backend.domain.chat.chatroom.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InviteJoinResponse {

	private Long id;
	private String inviteCode;
	private String name;
}
