package project.ai.stream.aireview;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import project.ai.client.internal.AiReviewInternalApiClient;
import project.common.message.aireview.AiReviewRequestMessage;

/**
 * AI 리뷰 요청 중 처리하다 멈춘 메시지(PEL)를 다시 처리한다.
 *
 * 1. 마지막 전달 후 5분이 지난 메시지를 찾는다.
 * 2. Heartbeat가 살아 있으면 아직 처리 중이라 건드리지 않는다. 경과 시간만으로는 죽은 작업과 오래 걸리는 작업을
 *    구분할 수 없어서, 시간은 후보를 고르는 데만 쓰고 판단은 Heartbeat로 한다.
 * 3. Heartbeat가 없으면 XCLAIM으로 가져와 다시 처리한다. 처리 권한은 reclaim=true로 요청해, 멈춘 Worker가 남긴
 *    PROCESSING 상태를 넘겨받는다.
 * 4. 전달 횟수가 3번을 넘으면 더 시도하지 않고 데드레터로 옮긴다. 이때 "실패" 결과를 보내 리뷰 전체가 끝날 수 있게 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiReviewPelReclaimScheduler {

    private static final String CLAIMER_CONSUMER = "ai-review-reclaimer";
    static final Duration IDLE_THRESHOLD = Duration.ofMinutes(5);
    private static final long INTERVAL_SECONDS = 60;
    private static final int BATCH_SIZE = 100;
    static final long MAX_DELIVERY_COUNT = 3;

    private final StringRedisTemplate stringRedisTemplate;
    private final AiReviewRequestConsumer aiReviewRequestConsumer;
    private final AiReviewHeartbeat heartbeat;
    private final AiReviewResultProducer aiReviewResultProducer;
    private final AiReviewInternalApiClient aiReviewInternalApiClient;
    private final ObjectMapper objectMapper;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "ai-review-pel-reclaim");
        t.setDaemon(true);
        return t;
    });

    @PostConstruct
    void start() {
        scheduler.scheduleWithFixedDelay(this::reclaimStaleMessages, INTERVAL_SECONDS, INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    @PreDestroy
    void stop() {
        scheduler.shutdownNow();
    }

    void reclaimStaleMessages() {
        try {
            PendingMessages pending = stringRedisTemplate.opsForStream().pending(
                AiReviewRequestConsumer.STREAM_KEY, AiReviewRequestConsumer.GROUP_NAME, Range.unbounded(), BATCH_SIZE);
            if (pending.isEmpty()) return;

            for (PendingMessage p : pending) {
                if (p.getElapsedTimeSinceLastDelivery().compareTo(IDLE_THRESHOLD) < 0) continue;
                String recordId = p.getIdAsString();
                if (heartbeat.isAlive(recordId)) continue; // 아직 처리 중

                if (p.getTotalDeliveryCount() > MAX_DELIVERY_COUNT) {
                    moveToDeadLetter(p.getId());
                } else {
                    reclaim(p.getId());
                }
            }
        } catch (Exception e) {
            log.error("PEL 재처리 실패 - stream={}", AiReviewRequestConsumer.STREAM_KEY, e);
        }
    }

    private void reclaim(RecordId id) {
        List<MapRecord<String, Object, Object>> claimed = stringRedisTemplate.opsForStream().claim(
            AiReviewRequestConsumer.STREAM_KEY, AiReviewRequestConsumer.GROUP_NAME, CLAIMER_CONSUMER, IDLE_THRESHOLD, id);
        if (claimed.isEmpty()) return; // 그사이 다른 쪽이 가져갔거나 ACK됨

        log.warn("멈춘 AI 리뷰 메시지 재처리 - recordId={}", id);
        aiReviewRequestConsumer.handle(toObjectRecord(claimed.get(0)), true);
    }

    private void moveToDeadLetter(RecordId id) {
        List<MapRecord<String, Object, Object>> raw = stringRedisTemplate.opsForStream()
            .range(AiReviewRequestConsumer.STREAM_KEY, Range.closed(id.getValue(), id.getValue()));
        String payload = raw.isEmpty() ? null : raw.get(0).getValue().values().iterator().next().toString();
        log.error("최대 재시도 초과, 데드레터로 이동 - recordId={}", id);

        aiReviewInternalApiClient.reportDeadLetter(AiReviewRequestConsumer.STREAM_KEY, id.getValue(), payload);
        publishGiveUp(payload);
        stringRedisTemplate.opsForStream().acknowledge(AiReviewRequestConsumer.STREAM_KEY, AiReviewRequestConsumer.GROUP_NAME, id);
    }

    /** 포기한 파일도 "실패"로 끝내야, 그 PR의 리뷰 전체가 대기 상태에 머물지 않고 완료(또는 실패)로 끝난다. */
    private void publishGiveUp(String payload) {
        if (payload == null) return;
        try {
            AiReviewRequestMessage message = objectMapper.readValue(payload, AiReviewRequestMessage.class);
            aiReviewResultProducer.publishFail(message.aiReviewId(), message.chatRoomId(), message.filePath(),
                message.commitSha(), "최대 재시도 초과로 리뷰하지 못함");
        } catch (Exception e) {
            log.error("포기한 메시지의 실패 결과 발행 실패", e);
        }
    }

    private static ObjectRecord<String, String> toObjectRecord(MapRecord<String, Object, Object> raw) {
        String value = raw.getValue().values().iterator().next().toString();
        return StreamRecords.newRecord()
            .in(AiReviewRequestConsumer.STREAM_KEY)
            .withId(raw.getId())
            .ofObject(value);
    }
}
