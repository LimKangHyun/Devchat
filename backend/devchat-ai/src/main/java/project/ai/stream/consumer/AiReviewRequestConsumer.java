package project.ai.stream.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;
import project.common.message.AiReviewRequestMessage;
import project.ai.processor.AiReviewProcessor;
import project.ai.stream.producer.AiReviewResultProducer;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiReviewRequestConsumer implements StreamListener<String, ObjectRecord<String, String>> {

    private final ObjectMapper objectMapper;
    private final AiReviewProcessor aiReviewProcessor;
    private final AiReviewResultProducer aiReviewResultProducer;

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
        }
    }
}