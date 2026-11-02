package project.ai.config;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import project.ai.client.internal.AiReviewInternalApiClient;
import project.ai.stream.AckOnSuccessListener;
import project.ai.stream.StreamConsumerGroups;
import project.ai.stream.StreamPollTimeout;
import project.ai.stream.StreamSubscriptions;
import project.ai.stream.aireview.AiReviewRequestConsumer;
import project.ai.stream.git.GitSummaryRequestConsumer;
import project.ai.stream.index.DeleteIndexConsumer;
import project.ai.stream.index.FileReindexConsumer;
import project.ai.stream.index.RepoIndexConsumer;
import project.ai.stream.reclaim.DeadLetterHandler;
import project.ai.stream.reclaim.PendingMessageReclaimer;
import project.ai.stream.reclaim.StreamReclaimMetrics;
import project.ai.stream.reclaim.StreamReclaimScheduler;
import project.ai.stream.reclaim.StreamReclaimScheduler.StreamGroup;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class RedisStreamConfig {

    private static final String AI_REVIEW_STREAM = "stream:ai-review:request";
    private static final String AI_REVIEW_GROUP = "ai-review-group";
    private static final String AI_REVIEW_CONSUMER = "ai-review-1";

    private static final String REPO_INDEX_STREAM = RepoIndexConsumer.STREAM_KEY;
    private static final String REPO_INDEX_GROUP = RepoIndexConsumer.GROUP_NAME;
    private static final String REPO_INDEX_CONSUMER = "ai-repo-index-1";

    private static final String FILE_REINDEX_STREAM = FileReindexConsumer.STREAM_KEY;
    private static final String FILE_REINDEX_GROUP = FileReindexConsumer.GROUP_NAME;
    private static final String FILE_REINDEX_CONSUMER = "ai-file-reindex-1";

    private static final String DELETE_INDEX_STREAM = DeleteIndexConsumer.STREAM_KEY;
    private static final String DELETE_INDEX_GROUP = DeleteIndexConsumer.GROUP_NAME;
    private static final String DELETE_INDEX_CONSUMER = "ai-delete-index-1";

    private static final String GIT_SUMMARY_STREAM = GitSummaryRequestConsumer.STREAM_KEY;
    private static final String GIT_SUMMARY_GROUP = GitSummaryRequestConsumer.GROUP_NAME;
    private static final String GIT_SUMMARY_CONSUMER = "ai-git-summary-1";

    /** 스트림 폴링 대기. Redis 명령 시간 제한보다 길면 StreamPollTimeout이 줄인다. */
    static final Duration POLL_TIMEOUT = Duration.ofSeconds(2);

    private final RedisConnectionFactory redisConnectionFactory;
    private final StringRedisTemplate stringRedisTemplate;

    /** 마지막 전달 뒤 이만큼 ACK가 없으면 멈춘 것으로 보고 다시 처리한다. 메시지 하나 처리 시간보다 길어야 한다 */
    @Value("${devchat.stream.reclaim.idle-threshold:5m}")
    private Duration reclaimIdleThreshold;
    /** 처음 전달을 포함한 최대 시도 횟수. 그래도 남아 있으면 데드레터로 보낸다 */
    @Value("${devchat.stream.reclaim.max-deliveries:3}")
    private long reclaimMaxDeliveries;
    @Value("${devchat.stream.reclaim.interval:30s}")
    private Duration reclaimInterval;
    /**
     * 이 PR을 처음 배포할 때 한 번, 이보다 오래된 PEL 메시지는 다시 처리하지 않고 ACK만 한다(ACK하지 않던 시절의 메시지).
     * 예전 PEL은 대부분 이미 처리한 메시지라, 길게 잡을수록 처리한 요약·재인덱싱을 다시 돌린다.
     */
    @Value("${devchat.stream.reclaim.legacy-cutoff:10m}")
    private Duration reclaimLegacyCutoff;

    @PostConstruct
    public void initConsumerGroups() {
        createGroupIfNotExists(AI_REVIEW_STREAM, AI_REVIEW_GROUP);
        createGroupIfNotExists(REPO_INDEX_STREAM, REPO_INDEX_GROUP);
        createGroupIfNotExists(FILE_REINDEX_STREAM, FILE_REINDEX_GROUP);
        createGroupIfNotExists(DELETE_INDEX_STREAM, DELETE_INDEX_GROUP);
        createGroupIfNotExists(GIT_SUMMARY_STREAM, GIT_SUMMARY_GROUP);
    }

    private void createGroupIfNotExists(String streamKey, String groupName) {
        consumerGroups().createIfNotExists(streamKey, groupName);
    }

    private StreamConsumerGroups consumerGroups() {
        return new StreamConsumerGroups(stringRedisTemplate);
    }

    @Bean
    public StreamMessageListenerContainer<String, ObjectRecord<String, String>> streamListenerContainer() {
        // 한 번에 한 메시지만 읽는다. 이유는 StreamSubscriptions.containerOptions에 있다
        var options = StreamSubscriptions.containerOptions(
                StreamPollTimeout.safe(POLL_TIMEOUT, StreamPollTimeout.commandTimeoutOf(redisConnectionFactory)));

        return StreamMessageListenerContainer.create(redisConnectionFactory, options);
    }

    /**
     * 구독을 등록하고 컨테이너를 시작한다. 오류가 나도 구독을 끊지 않는 이유는 StreamSubscriptions에 있다.
     * AI 리뷰를 뺀 네 스트림은 AckOnSuccessListener로 감싸, 처리하면 ACK하고 실패하면 PEL에 남겨 재처리한다.
     * (AI 리뷰는 AiReviewRequestConsumer가 직접 ACK한다.)
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

        var subscriptions = new StreamSubscriptions(streamListenerContainer, meterRegistry, consumerGroups());
        subscriptions.register(AI_REVIEW_STREAM, AI_REVIEW_GROUP, AI_REVIEW_CONSUMER, aiReviewConsumer);
        subscriptions.register(REPO_INDEX_STREAM, REPO_INDEX_GROUP, REPO_INDEX_CONSUMER,
            ackOnSuccess(repoIndexingConsumer, REPO_INDEX_STREAM, REPO_INDEX_GROUP));
        subscriptions.register(FILE_REINDEX_STREAM, FILE_REINDEX_GROUP, FILE_REINDEX_CONSUMER,
            ackOnSuccess(fileReindexConsumer, FILE_REINDEX_STREAM, FILE_REINDEX_GROUP));
        subscriptions.register(DELETE_INDEX_STREAM, DELETE_INDEX_GROUP, DELETE_INDEX_CONSUMER,
            ackOnSuccess(deleteIndexConsumer, DELETE_INDEX_STREAM, DELETE_INDEX_GROUP));
        subscriptions.register(GIT_SUMMARY_STREAM, GIT_SUMMARY_GROUP, GIT_SUMMARY_CONSUMER,
            ackOnSuccess(gitSummaryRequestConsumer, GIT_SUMMARY_STREAM, GIT_SUMMARY_GROUP));

        streamListenerContainer.start();
        return subscriptions;
    }

    @Bean
    public StreamReclaimMetrics streamReclaimMetrics(MeterRegistry meterRegistry) {
        return new StreamReclaimMetrics(meterRegistry);
    }

    /**
     * AI 리뷰를 뺀 네 스트림의 PEL 재처리. 시작할 때 예전 PEL을 정리하고, 그 뒤 주기마다 멈춘 메시지를 다시 처리한다.
     * AI 리뷰 스트림은 Heartbeat로 "오래 걸리는 리뷰"를 구분해야 해서 AiReviewPelReclaimScheduler가 그대로 맡고,
     * 여기서는 pending 지표만 갱신한다.
     */
    @Bean(destroyMethod = "stop")
    public StreamReclaimScheduler streamReclaimScheduler(
            StreamReclaimMetrics metrics,
            AiReviewInternalApiClient internalApiClient,
            RepoIndexConsumer repoIndexingConsumer,
            FileReindexConsumer fileReindexConsumer,
            DeleteIndexConsumer deleteIndexConsumer,
            GitSummaryRequestConsumer gitSummaryRequestConsumer) {

        // API 서버의 stream_dead_letter 테이블에 스트림 구분 없이 남긴다. 기록이 실패하면 예외가 나가 ACK하지 않고
        // 다음에 다시 기록을 시도한다(PendingMessageReclaimer)
        DeadLetterHandler deadLetter = internalApiClient::recordDeadLetter;

        List<PendingMessageReclaimer> reclaimers = List.of(
            // 재처리는 onReclaimed로 넘겨, 이미 끝났거나 만료된 요청을 다시 인덱싱하지 않게 한다
            reclaimer(REPO_INDEX_STREAM, REPO_INDEX_GROUP,
                ackOnSuccess(repoIndexingConsumer::onReclaimed, REPO_INDEX_STREAM, REPO_INDEX_GROUP), metrics, deadLetter),
            reclaimer(FILE_REINDEX_STREAM, FILE_REINDEX_GROUP,
                ackOnSuccess(fileReindexConsumer, FILE_REINDEX_STREAM, FILE_REINDEX_GROUP), metrics, deadLetter),
            reclaimer(DELETE_INDEX_STREAM, DELETE_INDEX_GROUP,
                ackOnSuccess(deleteIndexConsumer, DELETE_INDEX_STREAM, DELETE_INDEX_GROUP), metrics, deadLetter),
            reclaimer(GIT_SUMMARY_STREAM, GIT_SUMMARY_GROUP,
                ackOnSuccess(gitSummaryRequestConsumer, GIT_SUMMARY_STREAM, GIT_SUMMARY_GROUP), metrics, deadLetter));

        metrics.register(AI_REVIEW_STREAM);
        return new StreamReclaimScheduler(reclaimers, List.of(new StreamGroup(AI_REVIEW_STREAM, AI_REVIEW_GROUP)),
            stringRedisTemplate, metrics, reclaimLegacyCutoff, reclaimInterval);
    }

    /**
     * 구독이 시작된 뒤에 재처리를 시작한다. 먼저 예전 PEL을 정리하고 스트림별 ACK 수를 INFO로 남긴다.
     * (예전에는 여기서 PEL이 남아 있으면 WARN을 남겼는데, ACK하지 않던 메시지 때문에 시작할 때마다 떴다.)
     */
    @EventListener(ApplicationReadyEvent.class)
    public void startReclaim(ApplicationReadyEvent event) {
        event.getApplicationContext().getBean(StreamReclaimScheduler.class).start();
    }

    private PendingMessageReclaimer reclaimer(String streamKey, String group,
                                              StreamListener<String, ObjectRecord<String, String>> listener,
                                              StreamReclaimMetrics metrics, DeadLetterHandler deadLetter) {
        return new PendingMessageReclaimer(stringRedisTemplate, metrics, streamKey, group, group + "-reclaimer",
            listener, reclaimIdleThreshold, reclaimMaxDeliveries, deadLetter);
    }

    private AckOnSuccessListener ackOnSuccess(StreamListener<String, ObjectRecord<String, String>> listener,
                                              String streamKey, String group) {
        return new AckOnSuccessListener(listener, stringRedisTemplate, streamKey, group);
    }
}
