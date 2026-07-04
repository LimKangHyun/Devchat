package project.ai.stream.git;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import project.common.message.git.GitSummaryResultMessage;

@Slf4j
@Component
@RequiredArgsConstructor
public class GitSummaryResultProducer {

    private static final String STREAM_KEY = "stream:git-summary:result";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public void publish(Long roomId, Long messageId, String summarizedContent) {
        try {
            String json = objectMapper.writeValueAsString(new GitSummaryResultMessage(roomId, messageId, summarizedContent));
            ObjectRecord<String, String> record = StreamRecords.newRecord()
                .ofObject(json)
                .withStreamKey(STREAM_KEY);
            stringRedisTemplate.opsForStream().add(record);
            log.info("Git 요약 결과 발행: roomId={}", roomId);
        } catch (Exception e) {
            log.error("Git 요약 결과 발행 실패: roomId={}", roomId, e);
        }
    }
}