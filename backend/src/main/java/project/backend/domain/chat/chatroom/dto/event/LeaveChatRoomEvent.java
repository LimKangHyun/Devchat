package project.backend.domain.chat.chatroom.dto.event;

import java.time.LocalDateTime;

public record LeaveChatRoomEvent(Long roomId, Long memberId, String nickname,
								LocalDateTime leaveAt) {

}