package project.ai.stream.index;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import project.common.message.index.RepoIndexResultMessage;

@Component
@RequiredArgsConstructor
public class RepoIndexResultProducer {

    private static final String INDEX_RESULT_STREAM = "stream:repo-index:result";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public void publishSuccess(Long roomId) {
        publish(new RepoIndexResultMessage(roomId, true, null));
    }

    public void publishFail(Long roomId, String errorMessage) {
        publish(new RepoIndexResultMessage(roomId, false, errorMessage));
    }

    private void publish(RepoIndexResultMessage message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            ObjectRecord<String, String> record = StreamRecords.newRecord()
                    .ofObject(json)
                    .withStreamKey(INDEX_RESULT_STREAM);
            stringRedisTemplate.opsForStream().add(record);
        } catch (Exception e) {
            throw new RuntimeException("Redis Stream 발행 실패", e);
        }
    }
}