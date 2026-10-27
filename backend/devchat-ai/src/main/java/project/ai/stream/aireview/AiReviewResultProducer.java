package project.ai.stream.aireview;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import project.common.dto.InlineReview;
import project.common.message.aireview.AiReviewResultMessage;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiReviewResultProducer {

    private static final String STREAM_KEY = "stream:ai-review:result";

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    /** @param commitSha 요청의 커밋. API 서버가 그사이 새 커밋으로 리뷰가 다시 시작됐는지 판단하는 데 쓴다. */
    public void publishSuccess(Long aiReviewId, Long chatRoomId, String filePath, String commitSha,
                               List<InlineReview> reviews) {
        publish(new AiReviewResultMessage(aiReviewId, chatRoomId, filePath, "SUCCESS", reviews, null, commitSha));
    }

    public void publishFail(Long aiReviewId, Long chatRoomId, String filePath, String commitSha, String errorMessage) {
        publish(new AiReviewResultMessage(aiReviewId, chatRoomId, filePath, "FAIL", List.of(), errorMessage, commitSha));
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
            // 예전에는 로그만 남겨, 결과가 사라졌는데도 요청 메시지는 ACK됐다. 예외를 던져 호출한 쪽이 ACK를 미루게 한다.
            throw new ResultPublishException("AI Review 결과 발행 실패 - aiReviewId=" + message.aiReviewId(), e);
        }
    }

    /** 결과를 Redis Stream에 넣지 못함. 요청 메시지를 ACK하지 않고 재처리해야 한다. */
    public static class ResultPublishException extends RuntimeException {
        ResultPublishException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}