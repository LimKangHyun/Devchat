package project.api.global.redis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import project.api.domain.aireview.stream.AiReviewResultConsumer;
import project.api.domain.aireview.stream.GitSummaryResultConsumer;
import project.api.domain.aireview.stream.RepoIndexResultConsumer;
import project.api.domain.aireview.stream.IndexingProgressRelay;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import project.common.message.index.IndexingKeys;

import java.time.Duration;

@Slf4j
@Configuration
@Profile("!test")
public class RedisStreamConfig {

    static final String AI_REVIEW_RESULT_STREAM = "stream:ai-review:result";
    static final String AI_REVIEW_RESULT_GROUP = "api-ai-review-group";
    static final String AI_REVIEW_RESULT_CONSUMER = "api-ai-review-1";

    static final String GIT_SUMMARY_RESULT_STREAM = "stream:git-summary:result";
    static final String GIT_SUMMARY_RESULT_GROUP = "api-git-summary-group";
    static final String GIT_SUMMARY_RESULT_CONSUMER = "api-git-summary-1";

    // AI 서버와 같은 이름을 써야 한다. 예전에는 "stream:repo-indexing:result"로 달라서 결과를 받지 못했다.
    static final String REPO_INDEX_RESULT_STREAM = IndexingKeys.RESULT_STREAM;
    static final String REPO_INDEX_RESULT_GROUP = "api-repo-indexing-group";
    static final String REPO_INDEX_RESULT_CONSUMER = "api-repo-indexing-1";

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
    public StringRedisTemplate streamStringRedisTemplate(
            RedisConnectionFactory streamRedisConnectionFactory) {
        return new StringRedisTemplate(streamRedisConnectionFactory);
    }

    @Bean
    public StreamMessageListenerContainer<String, ObjectRecord<String, String>> streamListenerContainer(
            RedisConnectionFactory streamRedisConnectionFactory,
            @Qualifier("streamStringRedisTemplate") StringRedisTemplate streamStringRedisTemplate,
            AiReviewResultConsumer aiReviewResultConsumer,
            GitSummaryResultConsumer gitSummaryResultConsumer,
            RepoIndexResultConsumer repoIndexResultConsumer) {

        var options = StreamMessageListenerContainer.StreamMessageListenerContainerOptions.builder()
                .pollTimeout(Duration.ofSeconds(2))
                .targetType(String.class)
                .build();

        var container = StreamMessageListenerContainer.create(streamRedisConnectionFactory, options);

        subscribe(container, streamStringRedisTemplate,
                AI_REVIEW_RESULT_STREAM, AI_REVIEW_RESULT_GROUP, AI_REVIEW_RESULT_CONSUMER, aiReviewResultConsumer);
        subscribe(container, streamStringRedisTemplate,
                GIT_SUMMARY_RESULT_STREAM, GIT_SUMMARY_RESULT_GROUP, GIT_SUMMARY_RESULT_CONSUMER, gitSummaryResultConsumer);
        subscribe(container, streamStringRedisTemplate,
                REPO_INDEX_RESULT_STREAM, REPO_INDEX_RESULT_GROUP, REPO_INDEX_RESULT_CONSUMER, repoIndexResultConsumer);
        return container;
    }

    /**
     * 컨슈머 그룹 구독. 처리에 성공하면 ACK하고, 예외가 나면 ACK하지 않아 PEL에 남긴다(재처리 스케줄러가 회수).
     *
     * 예전에는 container.receive()를 썼는데, 이 방식은 자동 ACK를 하지 않아 정상 처리한 메시지도 PEL에 남았다.
     * 또 리스너가 예외를 던지면 기본 설정(cancelOnError)이 구독 자체를 취소해서,
     * 서버를 재시작할 때까지 그 스트림의 새 메시지를 아무도 읽지 않았다.
     */
    private void subscribe(StreamMessageListenerContainer<String, ObjectRecord<String, String>> container,
                           StringRedisTemplate template, String stream, String group, String consumerName,
                           StreamListener<String, ObjectRecord<String, String>> listener) {
        StreamListener<String, ObjectRecord<String, String>> ackOnSuccess = record -> {
            listener.onMessage(record);
            template.opsForStream().acknowledge(group, record);
        };
        container.register(
                StreamMessageListenerContainer.StreamReadRequest
                        .builder(StreamOffset.create(stream, ReadOffset.lastConsumed()))
                        .consumer(Consumer.from(group, consumerName))
                        .autoAcknowledge(false)
                        .cancelOnError(e -> false)
                        .errorHandler(e -> log.error("스트림 메시지 처리 실패, PEL에 남겨 재처리. stream={}", stream, e))
                        .build(),
                ackOnSuccess);
    }

    /** AI 서버의 인덱싱 진행 알림(Pub/Sub) 구독. 스트림과 같은 Redis를 쓴다. */
    @Bean
    public RedisMessageListenerContainer indexingProgressListenerContainer(
            RedisConnectionFactory streamRedisConnectionFactory,
            IndexingProgressRelay indexingProgressRelay) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(streamRedisConnectionFactory);
        container.addMessageListener(indexingProgressRelay, new ChannelTopic(IndexingKeys.PROGRESS_CHANNEL));
        return container;
    }

    @Bean
    public ApplicationRunner initStreamConsumerGroupsAndStartContainer(
            @Qualifier("streamStringRedisTemplate") StringRedisTemplate streamStringRedisTemplate,
            StreamMessageListenerContainer<String, ObjectRecord<String, String>> streamListenerContainer) {
        return args -> {
            createGroupIfNotExists(streamStringRedisTemplate, AI_REVIEW_RESULT_STREAM, AI_REVIEW_RESULT_GROUP);
            createGroupIfNotExists(streamStringRedisTemplate, GIT_SUMMARY_RESULT_STREAM, GIT_SUMMARY_RESULT_GROUP);
            createGroupIfNotExists(streamStringRedisTemplate, REPO_INDEX_RESULT_STREAM, REPO_INDEX_RESULT_GROUP);

            streamListenerContainer.start();
            log.info("Stream listener container started");
        };
    }

    private void createGroupIfNotExists(StringRedisTemplate template, String streamKey, String groupName) {
        try {
            template.opsForStream()
                    .createGroup(streamKey, ReadOffset.from("0"), groupName);
            log.info("Consumer group created: {} on stream {}", groupName, streamKey);
        } catch (Exception e) {
            String rootMessage = getRootCauseMessage(e);
            if (rootMessage != null && rootMessage.contains("BUSYGROUP")) {
                log.info("Consumer group already exists: {}", groupName);
            } else {
                log.error("Consumer group 생성 실패: streamKey={}, group={}", streamKey, groupName, e);
            }
        }
    }

    private String getRootCauseMessage(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage();
    }
}