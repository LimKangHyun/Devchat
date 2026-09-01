package project.api.global.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import project.api.domain.aireview.stream.AiReviewResultConsumer;

import java.time.Duration;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class StreamPendingRecoveryScheduler {

    private static final Duration IDLE_THRESHOLD = Duration.ofMinutes(1);
    private static final int BATCH_SIZE = 100;

    @Qualifier("streamStringRedisTemplate")
    private final StringRedisTemplate streamStringRedisTemplate;

    private final AiReviewResultConsumer aiReviewResultConsumer;

    @Scheduled(fixedDelay = 60_000)
    public void recoverAiReviewPending() {
        recover("stream:ai-review:result", "api-ai-review-group", "api-ai-review-1",
                aiReviewResultConsumer);
    }

    private void recover(String streamKey, String group, String consumerName,
                         StreamListener<String, ObjectRecord<String, String>> listener) {
        try {
            PendingMessages pending = streamStringRedisTemplate.opsForStream()
                    .pending(streamKey, group, Range.unbounded(), BATCH_SIZE);

            if (pending.isEmpty()) return;

            List<RecordId> stale = pending.stream()
                    .filter(p -> p.getElapsedTimeSinceLastDelivery().compareTo(IDLE_THRESHOLD) > 0)
                    .map(PendingMessage::getId)
                    .toList();

            if (stale.isEmpty()) return;

            log.warn("pending 메시지 회수 시작 - stream={}, count={}", streamKey, stale.size());

            List<MapRecord<String, Object, Object>> claimed = streamStringRedisTemplate.opsForStream()
                    .claim(streamKey, group, consumerName, IDLE_THRESHOLD,
                            stale.toArray(new RecordId[0]));

            for (MapRecord<String, Object, Object> raw : claimed) {
                try {
                    // 컨테이너가 넘겨주던 것과 같은 형태(ObjectRecord<String,String>)로 변환해
                    // 동일한 컨슈머 로직을 재사용한다.
                    String value = raw.getValue().values().iterator().next().toString();
                    ObjectRecord<String, String> record = StreamRecords.newRecord()
                            .in(streamKey)
                            .withId(raw.getId())
                            .ofObject(value);

                    listener.onMessage(record);

                    // 재처리 성공 시에만 ACK
                    streamStringRedisTemplate.opsForStream().acknowledge(group, record);
                    log.info("pending 메시지 재처리 성공 - id={}", raw.getId());
                } catch (Exception e) {
                    // 여전히 실패하면 ACK하지 않아 다음 주기에 다시 시도
                    log.error("pending 메시지 재처리 실패 - id={}", raw.getId(), e);
                }
            }
        } catch (Exception e) {
            log.error("pending 회수 실패 - stream={}", streamKey, e);
        }
    }
}