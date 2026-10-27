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
import project.api.domain.aireview.stream.GitSummaryResultConsumer;
import project.api.domain.aireview.stream.RepoIndexResultConsumer;

import java.time.Duration;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class StreamPendingRecoveryScheduler {

    private static final Duration IDLE_THRESHOLD = Duration.ofMinutes(1);
    private static final Duration MAX_AGE = Duration.ofDays(1);
    private static final int BATCH_SIZE = 100;

    @Qualifier("streamStringRedisTemplate")
    private final StringRedisTemplate streamStringRedisTemplate;

    private final AiReviewResultConsumer aiReviewResultConsumer;
    private final GitSummaryResultConsumer gitSummaryResultConsumer;
    private final RepoIndexResultConsumer repoIndexResultConsumer;

    @Scheduled(fixedDelay = 60_000)
    public void recoverAiReviewPending() {
        recover(RedisStreamConfig.AI_REVIEW_RESULT_STREAM, RedisStreamConfig.AI_REVIEW_RESULT_GROUP,
                RedisStreamConfig.AI_REVIEW_RESULT_CONSUMER, aiReviewResultConsumer);
    }

    @Scheduled(fixedDelay = 60_000)
    public void recoverGitSummaryPending() {
        recover(RedisStreamConfig.GIT_SUMMARY_RESULT_STREAM, RedisStreamConfig.GIT_SUMMARY_RESULT_GROUP,
                RedisStreamConfig.GIT_SUMMARY_RESULT_CONSUMER, gitSummaryResultConsumer);
    }

    @Scheduled(fixedDelay = 60_000)
    public void recoverRepoIndexPending() {
        recover(RedisStreamConfig.REPO_INDEX_RESULT_STREAM, RedisStreamConfig.REPO_INDEX_RESULT_GROUP,
                RedisStreamConfig.REPO_INDEX_RESULT_CONSUMER, repoIndexResultConsumer);
    }

    private void recover(String streamKey, String group, String consumerName,
                         StreamListener<String, ObjectRecord<String, String>> listener) {
        try {
            PendingMessages pending = streamStringRedisTemplate.opsForStream()
                    .pending(streamKey, group, Range.unbounded(), BATCH_SIZE);

            if (pending.isEmpty()) return;

            List<RecordId> idle = pending.stream()
                    .filter(p -> p.getElapsedTimeSinceLastDelivery().compareTo(IDLE_THRESHOLD) > 0)
                    .map(PendingMessage::getId)
                    .toList();

            // 만들어진 지 오래된 메시지는 재처리하지 않고 정리한다. 계속 실패하는 메시지가 영원히 돌지 않게 하고,
            // 예전에 ACK하지 않아 쌓여 있던 메시지가 배포 직후 한꺼번에 재처리되지 않게 한다.
            // (마지막 전달 시각은 회수할 때마다 갱신되므로, 나이는 메시지 ID에 담긴 생성 시각으로 판단한다)
            long now = System.currentTimeMillis();
            List<RecordId> expired = idle.stream()
                    .filter(id -> now - id.getTimestamp() > MAX_AGE.toMillis())
                    .toList();
            if (!expired.isEmpty()) {
                streamStringRedisTemplate.opsForStream().acknowledge(streamKey, group, expired.toArray(new RecordId[0]));
                log.warn("오래된 pending 메시지 폐기 - stream={}, count={}, 기준={}", streamKey, expired.size(), MAX_AGE);
            }

            List<RecordId> stale = idle.stream().filter(id -> !expired.contains(id)).toList();
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