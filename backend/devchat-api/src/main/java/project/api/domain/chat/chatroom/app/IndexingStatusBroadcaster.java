package project.api.domain.chat.chatroom.app;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import project.api.domain.chat.chatroom.dto.IndexingStatusResponse;

/** 인덱싱 진행·결과를 채팅방 WebSocket(/topic/chat/{roomId})으로 보낸다. 화면은 type=INDEXING_PROGRESS로 구분한다. */
@Component
@RequiredArgsConstructor
public class IndexingStatusBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;

    public void send(Long roomId, IndexingStatusResponse status) {
        messagingTemplate.convertAndSend("/topic/chat/" + roomId, status);
    }
}
