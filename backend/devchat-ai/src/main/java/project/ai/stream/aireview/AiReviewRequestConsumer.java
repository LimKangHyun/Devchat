package project.ai.stream.aireview;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;
import project.ai.processor.AiReviewProcessor;
import project.common.message.aireview.AiReviewRequestMessage;
import com.fasterxml.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiReviewRequestConsumer implements StreamListener<String, ObjectRecord<String, String>> {

    private static final String STREAM_KEY = "stream:ai-review:request";
    private static final String GROUP_NAME = "ai-review-group";

    private final ObjectMapper objectMapper;
    private final AiReviewProcessor aiReviewProcessor;
    private final AiReviewResultProducer aiReviewResultProducer;
    private final StringRedisTemplate stringRedisTemplate;   // ← 추가

    @Override
    public void onMessage(ObjectRecord<String, String> record) {
        AiReviewRequestMessage message = null;
        try {
            message = objectMapper.readValue(record.getValue(), AiReviewRequestMessage.class);
            log.info("AI Review 요청 수신: aiReviewId={}, filePath={}", message.aiReviewId(), message.filePath());

            var reviews = aiReviewProcessor.process(message);
            aiReviewResultProducer.publishSuccess(message.aiReviewId(), message.chatRoomId(), message.filePath(), reviews);

        } catch (Exception e) {
            log.error("AI Review 처리 실패: aiReviewId={}", message != null ? message.aiReviewId() : "unknown", e);
            if (message != null) {
                aiReviewResultProducer.publishFail(message.aiReviewId(), message.chatRoomId(), message.filePath(), e.getMessage());
            }
        } finally {
            stringRedisTemplate.opsForStream().acknowledge(STREAM_KEY, GROUP_NAME, record.getId());
        }
    }
}