package project.backend.domain.aireview.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;
import project.backend.domain.aireview.app.AiReviewService;
import project.common.message.AiReviewResultMessage;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiReviewResultConsumer implements StreamListener<String, ObjectRecord<String, String>> {

    private final ObjectMapper objectMapper;
    private final AiReviewService aiReviewService;

    @Override
    public void onMessage(ObjectRecord<String, String> record) {
        try {
            AiReviewResultMessage message = objectMapper.readValue(record.getValue(), AiReviewResultMessage.class);
            log.info("AI Review 결과 수신: aiReviewId={}, filePath={}, status={}",
                    message.aiReviewId(), message.filePath(), message.status());

            if ("SUCCESS".equals(message.status())) {
                aiReviewService.saveFileReviewResult(
                        message.aiReviewId(), message.chatRoomId(), message.filePath(), message.reviews());
            } else {
                aiReviewService.saveFileReviewFail(
                        message.aiReviewId(), message.chatRoomId(), message.filePath(), message.errorMessage());
            }
        } catch (Exception e) {
            log.error("AI Review 결과 처리 실패: {}", e.getMessage(), e);
        }
    }
}