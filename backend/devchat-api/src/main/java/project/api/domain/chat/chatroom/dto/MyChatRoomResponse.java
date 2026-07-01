package project.api.domain.chat.chatroom.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class MyChatRoomResponse {
    private Long roomId;
    private String roomName;
    private int participantCount;
    private String inviteCode;
}
