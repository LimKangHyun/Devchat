package project.backend.domain.chat.chatroom.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import project.backend.domain.chat.chatroom.entity.IndexingStatus;

@Getter
@AllArgsConstructor
public class AllRoomsResponse {

    private Long roomId;
    private String inviteCode;
    private String roomName;
    private boolean alarmEnabled;
    private Long unreadCount;
    private String repositoryUrl;
    private IndexingStatus indexingStatus;
}
