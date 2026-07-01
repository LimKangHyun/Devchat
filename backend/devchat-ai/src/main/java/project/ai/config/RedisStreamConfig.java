package project.ai.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import project.ai.stream.consumer.AiReviewRequestConsumer;
import project.ai.stream.consumer.DeleteIndexConsumer;
import project.ai.stream.consumer.FileReindexConsumer;
import project.ai.stream.consumer.RepoIndexingConsumer;

import java.time.Duration;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class RedisStreamConfig {

    private static final String AI_REVIEW_STREAM = "stream:ai-review:request";
    private static final String AI_REVIEW_GROUP = "ai-group";
    private static final String AI_REVIEW_CONSUMER = "ai-1";

    private static final String INDEXING_STREAM = "stream:repo-indexing:request";
    private static final String INDEXING_GROUP = "ai-indexing-group";
    private static final String INDEXING_CONSUMER = "ai-indexing-1";

    private static final String FILE_REINDEX_STREAM = "stream:file-reindex:request";
    private static final String FILE_REINDEX_GROUP = "ai-file-reindex-group";
    private static final String FILE_REINDEX_CONSUMER = "ai-file-reindex-1";

    private static final String DELETE_INDEX_STREAM = "stream:delete-index:request";
    private static final String DELETE_INDEX_GROUP = "ai-delete-group";
    private static final String DELETE_INDEX_CONSUMER = "ai-delete-1";

    private final RedisConnectionFactory redisConnectionFactory;
    private final StringRedisTemplate stringRedisTemplate;

    @PostConstruct
    public void initConsumerGroups() {
        createGroupIfNotExists(AI_REVIEW_STREAM, AI_REVIEW_GROUP);
        createGroupIfNotExists(INDEXING_STREAM, INDEXING_GROUP);
        createGroupIfNotExists(FILE_REINDEX_STREAM, FILE_REINDEX_GROUP);
        createGroupIfNotExists(DELETE_INDEX_STREAM, DELETE_INDEX_GROUP);
    }

    private void createGroupIfNotExists(String streamKey, String groupName) {
        try {
            stringRedisTemplate.opsForStream().createGroup(streamKey, groupName);
        } catch (Exception e) {
            log.info("Consumer group already exists: {}", groupName);
        }
    }

    @Bean
    public StreamMessageListenerContainer<String, ObjectRecord<String, String>> streamListenerContainer(
            AiReviewRequestConsumer aiReviewConsumer,
            RepoIndexingConsumer repoIndexingConsumer,
            FileReindexConsumer fileReindexConsumer,
            DeleteIndexConsumer deleteIndexConsumer) {

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
                Consumer.from(INDEXING_GROUP, INDEXING_CONSUMER),
                StreamOffset.create(INDEXING_STREAM, ReadOffset.lastConsumed()),
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
        container.start();
        return container;
    }
}