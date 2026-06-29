package project.backend.domain.chat.chatroom.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatParticipantResponse {

	private Long memberId;

	private String nickname;

	private String profileImageUrl;

	private boolean isOwner;

}

