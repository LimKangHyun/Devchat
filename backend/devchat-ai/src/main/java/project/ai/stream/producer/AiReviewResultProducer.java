package project.ai.stream.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import project.common.dto.InlineReview;
import project.common.message.AiReviewResultMessage;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiReviewResultProducer {

    private static final String STREAM_KEY = "stream:ai-review:result";

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public void publishSuccess(Long aiReviewId, Long chatRoomId, String filePath, List<InlineReview> reviews) {
        publish(new AiReviewResultMessage(aiReviewId, chatRoomId, filePath, "SUCCESS", reviews, null));
    }

    public void publishFail(Long aiReviewId, Long chatRoomId, String filePath, String errorMessage) {
        publish(new AiReviewResultMessage(aiReviewId, chatRoomId, filePath, "FAIL", List.of(), errorMessage));
    }

    private void publish(AiReviewResultMessage message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            ObjectRecord<String, String> record = StreamRecords.newRecord()
                    .ofObject(json)
                    .withStreamKey(STREAM_KEY);
            redisTemplate.opsForStream().add(record);
            log.info("AI Review 결과 발행: aiReviewId={}, filePath={}, status={}",
                    message.aiReviewId(), message.filePath(), message.status());
        } catch (Exception e) {
            log.error("Redis Stream 결과 발행 실패: aiReviewId={}", message.aiReviewId(), e);
        }
    }
}