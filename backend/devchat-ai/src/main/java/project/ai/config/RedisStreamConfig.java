package project.ai.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
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
    public StreamMessageListenerContainer<String, ObjectRecord<String, String>> streamListenerContainer(
            AiReviewRequestConsumer aiReviewConsumer,
            RepoIndexConsumer repoIndexingConsumer,
            FileReindexConsumer fileReindexConsumer,
            DeleteIndexConsumer deleteIndexConsumer,
            GitSummaryRequestConsumer gitSummaryRequestConsumer) {

        var options = StreamMessageListenerContainer.StreamMessageListenerContainerOptions.builder()
                .pollTimeout(Duration.ofSeconds(2))
                .targetType(String.class)
                .build();

        var container = StreamMessageListenerContainer.create(redisConnectionFactory, options);

        container.receive(
                Consumer.from(AI_REVIEW_GROUP, AI_REVIEW_CONSUMER),
                StreamOffset.create(AI_REVIEW_STREAM, ReadOffset.lastConsumed()),
                aiReviewConsumer
        );

        container.receive(
                Consumer.from(REPO_INDEX_GROUP, REPO_INDEX_CONSUMER),
                StreamOffset.create(REPO_INDEX_STREAM, ReadOffset.lastConsumed()),
                repoIndexingConsumer
        );

        container.receive(
            Consumer.from(FILE_REINDEX_GROUP, FILE_REINDEX_CONSUMER),
            StreamOffset.create(FILE_REINDEX_STREAM, ReadOffset.lastConsumed()),
            fileReindexConsumer
        );

        container.receive(
            Consumer.from(DELETE_INDEX_GROUP, DELETE_INDEX_CONSUMER),
            StreamOffset.create(DELETE_INDEX_STREAM, ReadOffset.lastConsumed()),
            deleteIndexConsumer
        );

        container.receive(
            Consumer.from(GIT_SUMMARY_GROUP, GIT_SUMMARY_CONSUMER),
            StreamOffset.create(GIT_SUMMARY_STREAM, ReadOffset.lastConsumed()),
            gitSummaryRequestConsumer
        );

        container.start();
        return container;
    }
}