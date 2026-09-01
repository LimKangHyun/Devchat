package project.api.domain.aireview.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;
import project.api.domain.aireview.app.AiReviewService;
import project.common.message.aireview.AiReviewResultMessage;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiReviewResultConsumer implements StreamListener<String, ObjectRecord<String, String>> {

    private final ObjectMapper objectMapper;
    private final AiReviewService aiReviewService;

    @Override
    public void onMessage(ObjectRecord<String, String> record) {
        AiReviewResultMessage message;

        // 역직렬화 실패는 재시도해도 계속 실패한다(포이즌 메시지).
        // 여기서 예외를 던지면 이 메시지가 pending에 영구히 남아 컨슈머를 막으므로,
        // 로그만 남기고 ACK되도록 정상 리턴한다.
        try {
            message = objectMapper.readValue(record.getValue(), AiReviewResultMessage.class);
        } catch (Exception e) {
            log.error("AI Review 결과 역직렬화 실패, 메시지 폐기: raw={}", record.getValue(), e);
            return;
        }

        log.info("AI Review 결과 수신: aiReviewId={}, filePath={}, status={}",
                message.aiReviewId(), message.filePath(), message.status());

        // 처리 실패는 일시적일 수 있으므로(DB 커넥션 고갈, 데드락 등)
        // 예외를 그대로 던져 ACK를 막고 메시지가 pending으로 남아 재처리되게 한다.
        // 중복 재처리는 AiReviewFile의 UNIQUE 제약으로 방어된다.
        if ("SUCCESS".equals(message.status())) {
            aiReviewService.saveFileReviewResult(
                    message.aiReviewId(), message.chatRoomId(), message.filePath(), message.reviews());
        } else {
            aiReviewService.saveFileReviewFail(
                    message.aiReviewId(), message.chatRoomId(), message.filePath(), message.errorMessage());
        }
    }
}