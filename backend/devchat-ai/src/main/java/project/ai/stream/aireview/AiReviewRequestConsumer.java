package project.ai.stream.aireview;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;
import project.ai.client.internal.AiReviewInternalApiClient;
import project.ai.processor.AiReviewProcessor;
import project.common.message.aireview.AiReviewRequestMessage;
import project.common.message.aireview.FileClaimResult;

/**
 * AI 리뷰 요청 처리. 작업이 끝난 뒤에만 ACK해서, 처리 중에 Worker가 죽으면 메시지가 PEL에 남아 재처리된다.
 *
 * 흐름: 처리 권한 요청(claim) → Heartbeat 시작 → 리뷰 → 결과 발행 → ACK → Heartbeat 정리
 * - claim: 같은 파일을 두 Worker가 동시에 리뷰하지 않게 한다. 예전 커밋 요청이거나 이미 처리 중·완료면 건너뛴다.
 * - Heartbeat: 재처리 스케줄러가 "죽은 작업"과 "오래 걸리는 작업"을 구분하는 근거다.
 * - 결과 저장은 API 서버가 상태 조건부 UPDATE로 한 번만 반영한다(Heartbeat 판단이 어긋나 두 Worker가 처리해도 방어).
 *
 * 컨테이너 구독은 리스너가 예외를 던지면 구독을 취소하므로, 여기서는 예외를 밖으로 내보내지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiReviewRequestConsumer implements StreamListener<String, ObjectRecord<String, String>> {

    static final String STREAM_KEY = "stream:ai-review:request";
    static final String GROUP_NAME = "ai-review-group";

    private final ObjectMapper objectMapper;
    private final AiReviewProcessor aiReviewProcessor;
    private final AiReviewResultProducer aiReviewResultProducer;
    private final AiReviewInternalApiClient aiReviewInternalApiClient;
    private final AiReviewHeartbeat heartbeat;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public void onMessage(ObjectRecord<String, String> record) {
        handle(record, false);
    }

    /**
     * @param reclaim 재처리 스케줄러가 앞 Worker의 Heartbeat 만료를 확인하고 다시 넘긴 메시지면 true
     */
    void handle(ObjectRecord<String, String> record, boolean reclaim) {
        String recordId = record.getId().getValue();

        AiReviewRequestMessage message;
        try {
            message = objectMapper.readValue(record.getValue(), AiReviewRequestMessage.class);
        } catch (Exception e) {
            // 다시 시도해도 계속 실패하는 메시지(포이즌 메시지)라 버린다.
            log.error("AI Review 요청 역직렬화 실패, 메시지 폐기 - recordId={}, raw={}", recordId, record.getValue(), e);
            ack(recordId);
            return;
        }

        FileClaimResult claim;
        try {
            claim = aiReviewInternalApiClient.claim(message.aiReviewId(), message.filePath(), message.commitSha(), reclaim);
        } catch (Exception e) {
            // API 서버에 닿지 못함. ACK하지 않고 PEL에 남겨, Heartbeat가 없으니 재처리 스케줄러가 다시 시도한다.
            log.warn("claim 요청 실패, 나중에 재시도 - aiReviewId={}, filePath={}", message.aiReviewId(), message.filePath(), e);
            return;
        }
        if (claim != FileClaimResult.CLAIMED) {
            log.info("처리 권한 없음({}), 건너뜀 - aiReviewId={}, filePath={}", claim, message.aiReviewId(), message.filePath());
            ack(recordId);
            return;
        }

        log.info("AI Review 처리 시작 - aiReviewId={}, filePath={}, reclaim={}", message.aiReviewId(), message.filePath(), reclaim);
        heartbeat.start(recordId);
        try {
            publishResult(message);
            ack(recordId);
        } catch (Exception e) {
            // 결과 발행(Redis)마저 실패. ACK하지 않고 Heartbeat를 지워, 재처리 스케줄러가 곧 다시 시도하게 한다.
            log.error("AI Review 결과 발행 실패, 나중에 재시도 - aiReviewId={}, filePath={}",
                message.aiReviewId(), message.filePath(), e);
        } finally {
            heartbeat.stop(recordId);
        }
    }

    /**
     * 리뷰가 실패해도 "실패" 결과를 보내야 API 서버가 이 파일을 끝난 것으로 세어 전체 리뷰가 완료된다.
     * 결과 발행 자체가 실패하면(ResultPublishException) 그대로 던져 ACK를 미룬다.
     */
    private void publishResult(AiReviewRequestMessage message) {
        java.util.List<project.common.dto.InlineReview> reviews;
        try {
            reviews = aiReviewProcessor.process(message);
        } catch (RuntimeException e) {
            log.error("AI Review 처리 실패 - aiReviewId={}, filePath={}", message.aiReviewId(), message.filePath(), e);
            aiReviewResultProducer.publishFail(
                message.aiReviewId(), message.chatRoomId(), message.filePath(), message.commitSha(), e.getMessage());
            return;
        }
        aiReviewResultProducer.publishSuccess(
            message.aiReviewId(), message.chatRoomId(), message.filePath(), message.commitSha(), reviews);
    }

    private void ack(String recordId) {
        try {
            stringRedisTemplate.opsForStream().acknowledge(STREAM_KEY, GROUP_NAME, recordId);
        } catch (Exception e) {
            log.warn("ACK 실패, 재처리될 수 있음(결과 저장은 API 서버에서 한 번만 반영) - recordId={}", recordId, e);
        }
    }
}
