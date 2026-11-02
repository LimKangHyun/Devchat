package project.ai.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.Subscription;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * 실제 Redis로 "Redis가 잠깐 멈췄다 돌아와도 구독이 이어서 읽는다"를 확인한다.
 * Redis 컨테이너를 docker pause로 멈추면 읽기가 시간 초과로 실패한다(배포 중 redis-stream이 다시 뜨는 상황과 같은 종류의 오류).
 */
@Testcontainers(disabledWithoutDocker = true)
class StreamSubscriptionsRedisTest {

    private static final String STREAM = "stream:test:request";
    private static final String GROUP = "test-group";

    @Container
    private static final GenericContainer<?> REDIS =
        new GenericContainer<>(DockerImageName.parse("redis:7")).withExposedPorts(6379);

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redis;
    private StreamMessageListenerContainer<String, ObjectRecord<String, String>> container;

    @BeforeEach
    void setUp() {
        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
            .commandTimeout(Duration.ofMillis(500))
            .clientOptions(ClientOptions.builder()
                .socketOptions(SocketOptions.builder().connectTimeout(Duration.ofMillis(500)).build())
                .build())
            .build();
        connectionFactory = new LettuceConnectionFactory(
            new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)), clientConfig);
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();

        redis = new StringRedisTemplate(connectionFactory);
        redis.delete(STREAM);
        redis.opsForStream().createGroup(STREAM, ReadOffset.from("0"), GROUP);

        var options = StreamMessageListenerContainer.StreamMessageListenerContainerOptions.builder()
            .pollTimeout(Duration.ofMillis(100))
            .targetType(String.class)
            .build();
        container = StreamMessageListenerContainer.create(connectionFactory, options);
    }

    @AfterEach
    void tearDown() {
        container.stop();
        connectionFactory.destroy();
        // 테스트가 중간에 실패해도 다음 테스트가 멈춘 Redis를 쓰지 않게 한다
        if (REDIS.getDockerClient().inspectContainerCmd(REDIS.getContainerId()).exec().getState().getPaused()) {
            REDIS.getDockerClient().unpauseContainerCmd(REDIS.getContainerId()).exec();
        }
    }

    @Test
    void Redis가_잠깐_멈춰도_구독이_유지되고_다시_읽는다() {
        List<String> received = new CopyOnWriteArrayList<>();
        AtomicInteger errors = new AtomicInteger();
        var subscriptions = new StreamSubscriptions(container, new SimpleMeterRegistry(), (stream, group) -> {
            var real = new RateLimitedStreamErrorHandler(stream, () -> { });
            return t -> {
                errors.incrementAndGet();
                real.handleError(t);
            };
        });
        subscriptions.register(STREAM, GROUP, "c1", record -> received.add(record.getValue()));
        container.start();

        publish("before");
        await().atMost(Duration.ofSeconds(10)).until(() -> received.contains("before"));

        pauseRedis();
        try {
            await().atMost(Duration.ofSeconds(10)).until(() -> errors.get() >= 2);
            assertThat(subscriptions.isActive(STREAM)).isTrue();
        } finally {
            unpauseRedis();
        }

        publish("after");
        await().atMost(Duration.ofSeconds(15)).until(() -> received.contains("after"));
        assertThat(subscriptions.isActive(STREAM)).isTrue();
        assertThat(new RedisStreamSubscriptionsHealthIndicator(subscriptions).health().getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void Redis가_데이터_없이_다시_떠서_그룹이_사라져도_그룹을_다시_만들고_이어서_읽는다() {
        List<String> received = new CopyOnWriteArrayList<>();
        var subscriptions = new StreamSubscriptions(container, new SimpleMeterRegistry(), new StreamConsumerGroups(redis));
        subscriptions.register(STREAM, GROUP, "c1", record -> received.add(record.getValue()));
        container.start();

        publish("before");
        await().atMost(Duration.ofSeconds(10)).until(() -> received.contains("before"));

        // 데이터 없이 다시 뜬 redis-stream: 스트림도 그룹도 없다. 이대로면 XREADGROUP이 NOGROUP으로 계속 실패한다
        redis.delete(STREAM);
        await().atMost(Duration.ofSeconds(10)).ignoreExceptions() // 키가 없으면 XINFO가 오류를 낸다
            .until(() -> !redis.opsForStream().groups(STREAM).isEmpty());

        publish("after");
        await().atMost(Duration.ofSeconds(15)).until(() -> received.contains("after"));
        assertThat(subscriptions.isActive(STREAM)).isTrue();
    }

    @Test
    void 예전_방식인_receive는_같은_오류에_구독이_끊긴다() {
        // 장애 원인 재현: container.receive(...)는 cancelOnError=true라 읽기 오류 한 번에 구독이 취소된다
        Subscription legacy = container.receive(
            Consumer.from(GROUP, "legacy"),
            StreamOffset.create(STREAM, ReadOffset.lastConsumed()),
            record -> { });
        container.start();
        await().atMost(Duration.ofSeconds(10)).until(legacy::isActive);

        pauseRedis();
        try {
            await().atMost(Duration.ofSeconds(10)).until(() -> !legacy.isActive());
        } finally {
            unpauseRedis();
        }

        assertThat(legacy.isActive()).isFalse();
    }

    private void publish(String value) {
        redis.opsForStream().add(StreamRecords.newRecord().in(STREAM).ofObject(value));
    }

    private void pauseRedis() {
        REDIS.getDockerClient().pauseContainerCmd(REDIS.getContainerId()).exec();
    }

    private void unpauseRedis() {
        REDIS.getDockerClient().unpauseContainerCmd(REDIS.getContainerId()).exec();
    }
}
