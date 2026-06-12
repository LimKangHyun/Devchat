package project.backend.domain.chat.chatroom.event;

import java.time.LocalDateTime;

public record LeaveChatRoomEvent(Long roomId, Long memberId, String nickname,
								LocalDateTime leaveAt) {

}