package project.ai.config;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import project.ai.stream.StreamSubscriptions;
import project.ai.stream.aireview.AiReviewRequestConsumer;
import project.ai.stream.git.GitSummaryRequestConsumer;
import project.ai.stream.index.DeleteIndexConsumer;
import project.ai.stream.index.FileReindexConsumer;
import project.ai.stream.index.RepoIndexConsumer;

import java.time.Duration;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class RedisStreamConfig {

    private static final String AI_REVIEW_STREAM = "stream:ai-review:request";
    private static final String AI_REVIEW_GROUP = "ai-review-group";
    private static final String AI_REVIEW_CONSUMER = "ai-review-1";

    private static final String REPO_INDEX_STREAM = "stream:repo-index:request";
    private static final String REPO_INDEX_GROUP = "ai-repo-index-group";
    private static final String REPO_INDEX_CONSUMER = "ai-repo-index-1";

    private static final String FILE_REINDEX_STREAM = "stream:file-reindex:request";
    private static final String FILE_REINDEX_GROUP = "ai-file-reindex-group";
    private static final String FILE_REINDEX_CONSUMER = "ai-file-reindex-1";

    private static final String DELETE_INDEX_STREAM = "stream:delete-index:request";
    private static final String DELETE_INDEX_GROUP = "ai-delete-index-group";
    private static final String DELETE_INDEX_CONSUMER = "ai-delete-index-1";

    private static final String GIT_SUMMARY_STREAM = "stream:git-summary:request";
    private static final String GIT_SUMMARY_GROUP = "ai-git-summary-group";
    private static final String GIT_SUMMARY_CONSUMER = "ai-git-summary-1";

    private final RedisConnectionFactory redisConnectionFactory;
    private final StringRedisTemplate stringRedisTemplate;

    @PostConstruct
    public void initConsumerGroups() {
        createGroupIfNotExists(AI_REVIEW_STREAM, AI_REVIEW_GROUP);
        createGroupIfNotExists(REPO_INDEX_STREAM, REPO_INDEX_GROUP);
        createGroupIfNotExists(FILE_REINDEX_STREAM, FILE_REINDEX_GROUP);
        createGroupIfNotExists(DELETE_INDEX_STREAM, DELETE_INDEX_GROUP);
        createGroupIfNotExists(GIT_SUMMARY_STREAM, GIT_SUMMARY_GROUP);
    }

    private void createGroupIfNotExists(String streamKey, String groupName) {
        try {
            stringRedisTemplate.opsForStream()
                    .createGroup(streamKey, ReadOffset.from("0"), groupName);
            log.info("Consumer group created: {} on stream {}", groupName, streamKey);
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("BUSYGROUP")) {
                log.info("Consumer group already exists: {}", groupName);
            } else {
                log.error("Consumer group 생성 실패: streamKey={}, group={}", streamKey, groupName, e);
            }
        }
    }

    @Bean
    public StreamMessageListenerContainer<String, ObjectRecord<String, String>> streamListenerContainer() {
        var options = StreamMessageListenerContainer.StreamMessageListenerContainerOptions.builder()
                .pollTimeout(Duration.ofSeconds(2))
                .targetType(String.class)
                .build();

        return StreamMessageListenerContainer.create(redisConnectionFactory, options);
    }

    /**
     * 구독을 등록하고 컨테이너를 시작한다. 오류가 나도 구독을 끊지 않는 이유는 StreamSubscriptions에 있다.
     */
    @Bean
    public StreamSubscriptions streamSubscriptions(
            StreamMessageListenerContainer<String, ObjectRecord<String, String>> streamListenerContainer,
            MeterRegistry meterRegistry,
            AiReviewRequestConsumer aiReviewConsumer,
            RepoIndexConsumer repoIndexingConsumer,
            FileReindexConsumer fileReindexConsumer,
            DeleteIndexConsumer deleteIndexConsumer,
            GitSummaryRequestConsumer gitSummaryRequestConsumer) {

        var subscriptions = new StreamSubscriptions(streamListenerContainer, meterRegistry);
        subscriptions.register(AI_REVIEW_STREAM, AI_REVIEW_GROUP, AI_REVIEW_CONSUMER, aiReviewConsumer);
        subscriptions.register(REPO_INDEX_STREAM, REPO_INDEX_GROUP, REPO_INDEX_CONSUMER, repoIndexingConsumer);
        subscriptions.register(FILE_REINDEX_STREAM, FILE_REINDEX_GROUP, FILE_REINDEX_CONSUMER, fileReindexConsumer);
        subscriptions.register(DELETE_INDEX_STREAM, DELETE_INDEX_GROUP, DELETE_INDEX_CONSUMER, deleteIndexConsumer);
        subscriptions.register(GIT_SUMMARY_STREAM, GIT_SUMMARY_GROUP, GIT_SUMMARY_CONSUMER, gitSummaryRequestConsumer);

        streamListenerContainer.start();
        return subscriptions;
    }

    /**
     * 시작할 때 처리 확인(ACK)이 안 된 메시지(PEL)가 남아 있으면 스트림마다 WARN으로 알린다.
     *
     * 오래된 인덱싱 요청은 이미 지워진 채팅방 것일 수 있어서 자동으로 다시 처리하지 않는다.
     * 확인하고 정리하는 방법은 docs/runbook/redis-stream-pending.md에 있다.
     * (AI 리뷰 스트림은 AiReviewPelReclaimScheduler가 따로 재처리한다.)
     */
    @EventListener(ApplicationReadyEvent.class)
    public void warnPendingMessages() {
        warnPending(AI_REVIEW_STREAM, AI_REVIEW_GROUP);
        warnPending(REPO_INDEX_STREAM, REPO_INDEX_GROUP);
        warnPending(FILE_REINDEX_STREAM, FILE_REINDEX_GROUP);
        warnPending(DELETE_INDEX_STREAM, DELETE_INDEX_GROUP);
        warnPending(GIT_SUMMARY_STREAM, GIT_SUMMARY_GROUP);
    }

    private void warnPending(String streamKey, String groupName) {
        try {
            PendingMessagesSummary summary = stringRedisTemplate.opsForStream().pending(streamKey, groupName);
            if (summary == null || summary.getTotalPendingMessages() == 0) return;
            log.warn("ACK 안 된 스트림 메시지가 남아 있음(자동 재처리 안 함) - stream={}, group={}, pending={}, oldestId={}",
                    streamKey, groupName, summary.getTotalPendingMessages(), summary.minMessageId());
        } catch (Exception e) {
            // 시작할 때 한 번 확인하는 것뿐이라, 실패해도 기동은 그대로 한다
            log.warn("PEL 확인 실패 - stream={}, group={}: {}", streamKey, groupName, e.getMessage());
        }
    }
}
