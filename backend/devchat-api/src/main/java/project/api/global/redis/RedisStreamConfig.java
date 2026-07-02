package project.api.global.redis;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import project.api.domain.aireview.stream.AiReviewResultConsumer;
import project.api.domain.aireview.stream.GitSummaryResultConsumer;
import project.api.domain.aireview.stream.RepoIndexResultConsumer;

import java.time.Duration;

@Slf4j
@Configuration
@Profile("!test")
public class RedisStreamConfig {

    private static final String AI_REVIEW_RESULT_STREAM = "stream:ai-review:result";
    private static final String AI_REVIEW_RESULT_GROUP = "api-ai-review-group";
    private static final String AI_REVIEW_RESULT_CONSUMER = "api-ai-review-1";

    private static final String GIT_SUMMARY_RESULT_STREAM = "stream:git-summary:result";
    private static final String GIT_SUMMARY_RESULT_GROUP = "api-git-summary-group";
    private static final String GIT_SUMMARY_RESULT_CONSUMER = "api-git-summary-1";

    private static final String REPO_INDEX_RESULT_STREAM = "stream:repo-indexing:result";
    private static final String REPO_INDEX_RESULT_GROUP = "api-repo-indexing-group";
    private static final String REPO_INDEX_RESULT_CONSUMER = "api-repo-indexing-1";


    @Value("${stream.redis.host}")
    private String host;

    @Value("${stream.redis.port}")
    private int port;

    @Bean
    public RedisConnectionFactory streamRedisConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(host, port);
        return new LettuceConnectionFactory(config);
    }

    @Bean("streamStringRedisTemplate")
    public StringRedisTemplate streamStringRedisTemplate() {
        return new StringRedisTemplate(streamRedisConnectionFactory());
    }

    @PostConstruct
    public void initConsumerGroups() {
        createGroupIfNotExists(AI_REVIEW_RESULT_STREAM, AI_REVIEW_RESULT_GROUP);
        createGroupIfNotExists(GIT_SUMMARY_RESULT_STREAM, GIT_SUMMARY_RESULT_GROUP);
        createGroupIfNotExists(REPO_INDEX_RESULT_STREAM, REPO_INDEX_RESULT_GROUP);
    }

    private void createGroupIfNotExists(String streamKey, String groupName) {
        try {
            streamStringRedisTemplate().opsForStream().createGroup(streamKey, groupName);
        } catch (Exception e) {
            log.info("Consumer group already exists: {}", groupName);
        }
    }

    @Bean
    public StreamMessageListenerContainer<String, ObjectRecord<String, String>> streamListenerContainer(
            AiReviewResultConsumer aiReviewResultConsumer,
            GitSummaryResultConsumer gitSummaryResultConsumer,
            RepoIndexResultConsumer repoIndexResultConsumer) {

        var options = StreamMessageListenerContainer.StreamMessageListenerContainerOptions.builder()
                .pollTimeout(Duration.ofSeconds(2))
                .targetType(String.class)
                .build();

        var container = StreamMessageListenerContainer.create(streamRedisConnectionFactory(), options);

        container.receive(
                Consumer.from(AI_REVIEW_RESULT_GROUP, AI_REVIEW_RESULT_CONSUMER),
                StreamOffset.create(AI_REVIEW_RESULT_STREAM, ReadOffset.lastConsumed()),
                aiReviewResultConsumer
        );

        container.receive(
                Consumer.from(GIT_SUMMARY_RESULT_GROUP, GIT_SUMMARY_RESULT_CONSUMER),
                StreamOffset.create(GIT_SUMMARY_RESULT_STREAM, ReadOffset.lastConsumed()),
                gitSummaryResultConsumer
        );

        container.receive(
                Consumer.from(REPO_INDEX_RESULT_GROUP, REPO_INDEX_RESULT_CONSUMER),
                StreamOffset.create(REPO_INDEX_RESULT_STREAM, ReadOffset.lastConsumed()),
                repoIndexResultConsumer
        );

        container.start();
        return container;
    }
}