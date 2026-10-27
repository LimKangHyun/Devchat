package project.api.domain.aireview.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import project.api.domain.chat.chatroom.app.IndexingStatusBroadcaster;
import project.api.domain.chat.chatroom.dto.IndexingStatusResponse;
import project.common.message.index.IndexingProgressMessage;

/**
 * AI 서버의 인덱싱 진행 알림(Redis Pub/Sub)을 받아 채팅방 WebSocket으로 전달한다.
 * AI 서버에는 WebSocket이 없어서 API 서버가 대신 보낸다.
 * Stream이 아니라 Pub/Sub을 쓰는 이유: 진행률은 놓쳐도 다음 알림으로 덮이는 값이고,
 * API 서버가 여러 대일 때 각 서버에 붙은 사용자 모두에게 보내야 해서 모든 구독자가 받아야 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IndexingProgressRelay implements MessageListener {

    private final ObjectMapper objectMapper;
    private final IndexingStatusBroadcaster broadcaster;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            IndexingProgressMessage progress = objectMapper.readValue(
                    new String(message.getBody(), StandardCharsets.UTF_8), IndexingProgressMessage.class);
            broadcaster.send(progress.roomId(),
                    IndexingStatusResponse.of("RUNNING", progress.phase(), progress.done(), progress.total()));
        } catch (Exception e) {
            log.warn("인덱싱 진행 알림 전달 실패: {}", e.getMessage());
        }
    }
}
