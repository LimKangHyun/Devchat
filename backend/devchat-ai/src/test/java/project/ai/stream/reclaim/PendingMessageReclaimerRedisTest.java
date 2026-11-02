package project.ai.stream.reclaim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import project.ai.indexing.RepoIndexingService;
import project.ai.stream.AckOnSuccessListener;
import project.ai.stream.StreamConsumerGroups;
import project.ai.stream.StreamSubscriptions;
import project.ai.stream.index.RepoIndexConsumer;
import project.ai.stream.index.RepoIndexResultProducer;
import project.common.message.index.IndexingKeys;
import project.common.message.index.RepoIndexRequestMessage;

/**
 * 실제 Redis로 ACK·재처리·데드레터·예전 PEL 정리를 확인한다.
 * 메시지는 구독(컨테이너)으로 받아, 운영과 같이 XREADGROUP으로 전달된 PEL 상태에서 시작한다.
 */
@Testcontainers(disabledWithoutDocker = true)
class PendingMessageReclaimerRedisTest {

    private static final String STREAM = "stream:test:request";
    private static final String GROUP = "test-group";
    private static final Duration IDLE = Duration.ofSeconds(1);
    private static final long MAX_DELIVERIES = 3;

    @Container
    private static final GenericContainer<?> REDIS =
        new GenericContainer<>(DockerImageName.parse("redis:7")).withExposedPorts(6379);

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redis;
    private StreamMessageListenerContainer<String, ObjectRecord<String, String>> container;
    private SimpleMeterRegistry meterRegistry;
    private StreamReclaimMetrics metrics;
    private final List<String> deadLetters = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() {
        connectionFactory = new LettuceConnectionFactory(
            new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();

        redis = new StringRedisTemplate(connectionFactory);
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
        redis.opsForStream().createGroup(STREAM, ReadOffset.from("0"), GROUP);

        // 운영과 같은 설정(한 번에 한 메시지)
        container = StreamMessageListenerContainer.create(connectionFactory,
            StreamSubscriptions.containerOptions(Duration.ofMillis(100)));
        meterRegistry = new SimpleMeterRegistry();
        metrics = new StreamReclaimMetrics(meterRegistry);
    }

    @AfterEach
    void tearDown() {
        container.stop();
        connectionFactory.destroy();
    }

    @Test
    void 처리에_성공하면_ACK되어_PEL에_남지_않는다() {
        List<String> received = new CopyOnWriteArrayList<>();
        subscribe(ackOnSuccess(record -> received.add(record.getValue())));

        publish("hello");

        await().atMost(Duration.ofSeconds(10)).until(() -> received.contains("hello"));
        await().atMost(Duration.ofSeconds(5)).until(() -> pendingCount() == 0);
    }

    @Test
    void 처리에_실패하면_PEL에_남았다가_idle이_지나면_재처리되어_ACK된다() {
        AtomicInteger attempts = new AtomicInteger();
        List<String> processed = new CopyOnWriteArrayList<>();
        StreamListener<String, ObjectRecord<String, String>> listener = ackOnSuccess(record -> {
            if (attempts.incrementAndGet() == 1) throw new IllegalStateException("일시 장애");
            processed.add(record.getValue());
        });
        subscribe(listener);
        PendingMessageReclaimer reclaimer = reclaimer(listener);

        publish("retry-me");
        await().atMost(Duration.ofSeconds(10)).until(() -> attempts.get() == 1);
        assertThat(pendingCount()).isEqualTo(1);

        // idle이 지나기 전에는 건드리지 않는다
        reclaimer.reclaimOnce();
        assertThat(attempts.get()).isEqualTo(1);

        await().pollDelay(IDLE.plusMillis(100)).atMost(Duration.ofSeconds(5)).until(() -> true);
        reclaimer.reclaimOnce();

        assertThat(processed).containsExactly("retry-me");
        assertThat(pendingCount()).isZero();
        assertThat(meterRegistry.counter(StreamReclaimMetrics.RECLAIMED, "stream", STREAM).count()).isEqualTo(1);
        assertThat(deadLetters).isEmpty();
    }

    @Test
    void 최대_전달_횟수를_넘기면_데드레터로_보내고_ACK한다() {
        AtomicInteger attempts = new AtomicInteger();
        StreamListener<String, ObjectRecord<String, String>> listener = ackOnSuccess(record -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("계속 실패");
        });
        subscribe(listener);
        PendingMessageReclaimer reclaimer = reclaimer(listener);

        RecordId id = publish("poison");
        await().atMost(Duration.ofSeconds(10)).until(() -> attempts.get() == 1);

        // 처음 전달(1) + 재처리 2번 = 최대 3번 시도, 그다음 주기에 데드레터
        for (int i = 0; i < MAX_DELIVERIES; i++) {
            await().pollDelay(IDLE.plusMillis(100)).atMost(Duration.ofSeconds(5)).until(() -> true);
            reclaimer.reclaimOnce();
        }

        assertThat(attempts.get()).isEqualTo((int) MAX_DELIVERIES);
        assertThat(deadLetters).containsExactly(STREAM + "|" + id.getValue() + "|poison");
        assertThat(pendingCount()).isZero();
        assertThat(meterRegistry.counter(StreamReclaimMetrics.DEAD_LETTER, "stream", STREAM).count()).isEqualTo(1);
    }

    @Test
    void 시작할_때_기준보다_오래된_PEL은_다시_처리하지_않고_ACK만_한다() {
        List<String> processed = new CopyOnWriteArrayList<>();
        PendingMessageReclaimer reclaimer = reclaimer(ackOnSuccess(record -> processed.add(record.getValue())));

        long now = System.currentTimeMillis();
        // ACK하지 않던 시절에 전달된 메시지 둘(2시간 전, 90분 전)과 방금 전달된 메시지 하나
        redis.opsForStream().add(StreamRecords.newRecord().in(STREAM).ofObject("old-1").withId(RecordId.of(now - 7_200_000, 0)));
        redis.opsForStream().add(StreamRecords.newRecord().in(STREAM).ofObject("old-2").withId(RecordId.of(now - 5_400_000, 0)));
        redis.opsForStream().add(StreamRecords.newRecord().in(STREAM).ofObject("new"));
        readWithoutAck(3);
        assertThat(pendingCount()).isEqualTo(3);

        int acked = reclaimer.cleanUpLegacy(Duration.ofHours(1), now);

        assertThat(acked).isEqualTo(2);
        assertThat(processed).isEmpty();
        assertThat(pendingCount()).isEqualTo(1); // 새 메시지는 평소 재처리로 넘긴다

        // 한 번 정리한 뒤 시작할 때는 건너뛴다. 기준보다 오래 재시도 중인 메시지도 버리지 않는다
        assertThat(reclaimer.cleanUpLegacy(Duration.ofHours(1), now + 7_200_000)).isEqualTo(PendingMessageReclaimer.LEGACY_ALREADY_DONE);
        assertThat(reclaimer.refreshPendingCount()).isEqualTo(1);
    }

    @Test
    void 데드레터_기록이_실패하면_ACK하지_않고_다음_주기에_다시_기록한다() {
        AtomicInteger recordAttempts = new AtomicInteger();
        StreamListener<String, ObjectRecord<String, String>> listener = ackOnSuccess(record -> {
            throw new IllegalStateException("계속 실패");
        });
        PendingMessageReclaimer reclaimer = new PendingMessageReclaimer(redis, metrics, STREAM, GROUP, GROUP + "-reclaimer",
            listener, IDLE, 1, (stream, id, payload) -> {
                if (recordAttempts.incrementAndGet() == 1) throw new IllegalStateException("API 서버 장애");
                deadLetters.add(id);
            });
        RecordId id = publish("x".repeat(1_000));
        readWithoutAck(1);

        waitIdle();
        reclaimer.reclaimOnce();
        assertThat(recordAttempts.get()).isEqualTo(1);
        assertThat(pendingCount()).isEqualTo(1);
        assertThat(meterRegistry.counter(StreamReclaimMetrics.DEAD_LETTER, "stream", STREAM).count()).isZero();

        waitIdle();
        reclaimer.reclaimOnce();
        assertThat(deadLetters).containsExactly(id.getValue());
        assertThat(pendingCount()).isZero();
        assertThat(meterRegistry.counter(StreamReclaimMetrics.DEAD_LETTER, "stream", STREAM).count()).isEqualTo(1);
    }

    @Test
    void 오래_걸리는_메시지가_줄지어_있어도_기다리는_메시지는_PEL에_올라가지_않아_재처리되지_않는다() throws Exception {
        // 메시지 하나는 idle 기준보다 짧지만(0.6초), 다섯 개를 합치면 훨씬 길다(3초).
        // 한꺼번에 읽으면 뒤쪽 메시지가 기다리는 동안 idle 기준을 넘겨 재처리 스케줄러가 한 번 더 처리한다
        List<String> processed = new CopyOnWriteArrayList<>();
        StreamListener<String, ObjectRecord<String, String>> listener = ackOnSuccess(record -> {
            try {
                Thread.sleep(600);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            processed.add(record.getValue());
        });
        PendingMessageReclaimer reclaimer = reclaimer(listener);
        for (int i = 0; i < 5; i++) publish("slow-" + i);
        subscribe(listener);

        // 처리하는 동안 재처리 스케줄러를 계속 돌린다
        long deadline = System.currentTimeMillis() + 10_000;
        while (processed.size() < 5 && System.currentTimeMillis() < deadline) {
            reclaimer.reclaimOnce();
            Thread.sleep(100);
            assertThat(pendingCount()).isLessThanOrEqualTo(1);
        }

        assertThat(processed).containsExactly("slow-0", "slow-1", "slow-2", "slow-3", "slow-4");
        assertThat(meterRegistry.counter(StreamReclaimMetrics.RECLAIMED, "stream", STREAM).count()).isZero();
        assertThat(deadLetters).isEmpty();
    }

    private void waitIdle() {
        await().pollDelay(IDLE.plusMillis(100)).atMost(Duration.ofSeconds(5)).until(() -> true);
    }

    @Test
    void 오래_걸리는_인덱싱은_실행기에_넘길_때_ACK되어_돌고_있는_동안_다시_실행되지_않는다() throws Exception {
        long roomId = 42L;
        AtomicInteger runs = new AtomicInteger();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        ExecutorService async = Executors.newSingleThreadExecutor();
        // @Async indexRepository 흉내: 실행기에 넘기고 바로 돌아온다. 실제 인덱싱처럼 락을 잡고 오래 돈다
        RepoIndexingService indexing = mock(RepoIndexingService.class);
        doAnswer(inv -> {
            async.submit(() -> {
                redis.opsForValue().set(IndexingKeys.lockKey(roomId), "1");
                runs.incrementAndGet();
                started.countDown();
                finish.await();
                redis.delete(IndexingKeys.requestedKey(roomId));
                redis.delete(IndexingKeys.lockKey(roomId));
                return null;
            });
            return null;
        }).when(indexing).indexRepository(anyLong(), anyString(), anyLong());

        ObjectMapper objectMapper = new ObjectMapper();
        RepoIndexConsumer consumer = new RepoIndexConsumer(objectMapper, indexing, redis, mock(RepoIndexResultProducer.class));
        subscribe(ackOnSuccess(consumer));
        PendingMessageReclaimer reclaimer = reclaimer(ackOnSuccess(consumer::onReclaimed));

        redis.opsForValue().set(IndexingKeys.requestedKey(roomId), "1");
        publish(objectMapper.writeValueAsString(new RepoIndexRequestMessage(roomId, "https://github.com/a/b", 1L)));

        try {
            assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
            await().atMost(Duration.ofSeconds(5)).until(() -> pendingCount() == 0);

            // 인덱싱이 idle 기준보다 오래 돌아도 재처리 대상이 아니다
            await().pollDelay(IDLE.multipliedBy(2)).atMost(Duration.ofSeconds(5)).until(() -> true);
            reclaimer.reclaimOnce();

            assertThat(runs.get()).isEqualTo(1);
            assertThat(meterRegistry.counter(StreamReclaimMetrics.RECLAIMED, "stream", STREAM).count()).isZero();
        } finally {
            finish.countDown();
            async.shutdown();
        }
    }

    @Test
    void 실행기에_넘기기_전에_잃어버린_인덱싱_요청은_재처리하고_이미_끝난_요청은_건너뛴다() throws Exception {
        long liveRoom = 1L;
        long finishedRoom = 2L;
        List<Long> indexed = new CopyOnWriteArrayList<>();
        RepoIndexingService indexing = mock(RepoIndexingService.class);
        doAnswer(inv -> indexed.add(inv.getArgument(0))).when(indexing).indexRepository(anyLong(), anyString(), anyLong());
        ObjectMapper objectMapper = new ObjectMapper();
        RepoIndexConsumer consumer = new RepoIndexConsumer(objectMapper, indexing, redis, mock(RepoIndexResultProducer.class));
        PendingMessageReclaimer reclaimer = reclaimer(ackOnSuccess(consumer::onReclaimed));

        // 둘 다 전달됐지만 처리되지 않은 채 PEL에 남았다(응답 유실). liveRoom만 아직 요청 표시가 남아 있다
        redis.opsForValue().set(IndexingKeys.requestedKey(liveRoom), "1");
        publish(objectMapper.writeValueAsString(new RepoIndexRequestMessage(liveRoom, "https://github.com/a/b", 1L)));
        publish(objectMapper.writeValueAsString(new RepoIndexRequestMessage(finishedRoom, "https://github.com/a/c", 1L)));
        readWithoutAck(2);

        await().pollDelay(IDLE.plusMillis(100)).atMost(Duration.ofSeconds(5)).until(() -> true);
        reclaimer.reclaimOnce();

        assertThat(indexed).containsExactly(liveRoom);
        assertThat(pendingCount()).isZero(); // 건너뛴 요청도 처리한 것으로 보고 ACK한다
    }

    private PendingMessageReclaimer reclaimer(StreamListener<String, ObjectRecord<String, String>> listener) {
        return new PendingMessageReclaimer(redis, metrics, STREAM, GROUP, GROUP + "-reclaimer", listener,
            IDLE, MAX_DELIVERIES, (stream, id, payload) -> deadLetters.add(stream + "|" + id + "|" + payload));
    }

    private AckOnSuccessListener ackOnSuccess(StreamListener<String, ObjectRecord<String, String>> listener) {
        return new AckOnSuccessListener(listener, redis, STREAM, GROUP);
    }

    private void subscribe(StreamListener<String, ObjectRecord<String, String>> listener) {
        var subscriptions = new StreamSubscriptions(container, new SimpleMeterRegistry(), new StreamConsumerGroups(redis));
        subscriptions.register(STREAM, GROUP, "c1", listener);
        container.start();
    }

    private RecordId publish(String value) {
        return redis.opsForStream().add(StreamRecords.newRecord().in(STREAM).ofObject(value));
    }

    private void readWithoutAck(int count) {
        redis.opsForStream().read(Consumer.from(GROUP, "legacy-consumer"),
            StreamReadOptions.empty().count(count), StreamOffset.create(STREAM, ReadOffset.lastConsumed()));
    }

    private long pendingCount() {
        return redis.opsForStream().pending(STREAM, GROUP).getTotalPendingMessages();
    }
}
