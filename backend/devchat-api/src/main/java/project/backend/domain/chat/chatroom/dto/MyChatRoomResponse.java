package project.backend.domain.chat.chatroom.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.springframework.web.bind.annotation.GetMapping;

@Getter
@Builder
@AllArgsConstructor
public class MyChatRoomResponse {
    private Long roomId;
    private String roomName;
    private int participantCount;
    private String inviteCode;
}
