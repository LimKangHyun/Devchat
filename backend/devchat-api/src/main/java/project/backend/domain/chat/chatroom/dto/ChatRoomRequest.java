package project.backend.domain.chat.chatroom.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ChatRoomRequest {

	@NotBlank(message = "채팅방 이름을 설정해주세요")
	private String name;

	//	@NotBlank(message = "채팅방 레포지토리주소를 설정해주세요")
	private String repositoryUrl;

}
