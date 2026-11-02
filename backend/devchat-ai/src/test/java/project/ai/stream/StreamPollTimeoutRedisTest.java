package project.ai.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * 운영과 같은 spring.data.redis.timeout=500ms에서 스트림 구독을 돌려 본다.
 * 폴링 대기(2초)가 명령 시간 제한보다 길면 새 메시지가 없는 폴링마다 "Redis command timed out"이 났다(2026-11 장애).
 * StreamPollTimeout이 폴링 대기를 줄이면 오류 없이 기다리다가 새 메시지를 받아야 한다.
 */
@Testcontainers(disabledWithoutDocker = true)
class StreamPollTimeoutRedisTest {

    private static final String STREAM = "stream:test:poll-timeout";
    private static final String GROUP = "test-group";
    private static final Duration DESIRED_POLL = Duration.ofSeconds(2);
    private static final Duration IDLE = Duration.ofSeconds(5);

    @Container
    private static final GenericContainer<?> REDIS =
        new GenericContainer<>(DockerImageName.parse("redis:7")).withExposedPorts(6379);

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(RedisAutoConfiguration.class))
        .withPropertyValues(
            "spring.data.redis.host=" + REDIS.getHost(),
            "spring.data.redis.port=" + REDIS.getMappedPort(6379),
            "spring.data.redis.timeout=500ms", // 운영의 예전 값
            "spring.data.redis.connect-timeout=500ms");

    @Test
    void 명령_시간_제한이_500ms여도_폴링_대기를_줄여_오류_없이_기다리고_이후_메시지를_받는다() {
        contextRunner.run(context -> {
            RedisConnectionFactory factory = context.getBean(RedisConnectionFactory.class);
            Duration poll = StreamPollTimeout.safe(DESIRED_POLL, StreamPollTimeout.commandTimeoutOf(factory));
            assertThat(poll).isEqualTo(Duration.ofMillis(300));

            runSubscription(factory, poll, (errors, received) -> {
                // 메시지 없이 기다리는 동안 시간 초과가 하나도 없어야 한다
                await().pollDelay(IDLE).atMost(IDLE.plusSeconds(1)).until(() -> true);
                assertThat(errors).isEmpty();

                publish(factory, "after-idle");
                await().atMost(Duration.ofSeconds(5)).until(() -> received.contains("after-idle"));
                assertThat(errors).isEmpty();
            });
        });
    }

    @Test
    void 줄이지_않으면_메시지가_없을_때마다_시간_초과가_난다() {
        // 장애 재현: 2초 폴링 + 500ms 명령 시간 제한
        contextRunner.run(context -> {
            RedisConnectionFactory factory = context.getBean(RedisConnectionFactory.class);

            runSubscription(factory, DESIRED_POLL, (errors, received) -> {
                await().atMost(IDLE).until(() -> errors.size() >= 2);
                assertThat(errors).allSatisfy(t -> assertThat(t).isInstanceOf(QueryTimeoutException.class));
            });
        });
    }

    private void runSubscription(RedisConnectionFactory factory, Duration poll,
                                 BiConsumer<List<Throwable>, List<String>> scenario) {
        StringRedisTemplate redis = new StringRedisTemplate(factory);
        redis.delete(STREAM);
        redis.opsForStream().createGroup(STREAM, ReadOffset.from("0"), GROUP);

        var options = StreamMessageListenerContainer.StreamMessageListenerContainerOptions.builder()
            .pollTimeout(poll)
            .targetType(String.class)
            .build();
        StreamMessageListenerContainer<String, ObjectRecord<String, String>> container =
            StreamMessageListenerContainer.create(factory, options);

        List<Throwable> errors = new CopyOnWriteArrayList<>();
        List<String> received = new CopyOnWriteArrayList<>();
        var subscriptions = new StreamSubscriptions(container, new SimpleMeterRegistry(),
            (stream, group) -> errors::add);
        subscriptions.register(STREAM, GROUP, "c1", record -> received.add(record.getValue()));
        container.start();
        try {
            await().atMost(Duration.ofSeconds(5)).until(() -> subscriptions.isActive(STREAM));
            scenario.accept(errors, received);
            assertThat(subscriptions.isActive(STREAM)).isTrue();
        } finally {
            container.stop();
        }
    }

    private static void publish(RedisConnectionFactory factory, String value) {
        new StringRedisTemplate(factory).opsForStream()
            .add(StreamRecords.newRecord().in(STREAM).ofObject(value));
    }
}
