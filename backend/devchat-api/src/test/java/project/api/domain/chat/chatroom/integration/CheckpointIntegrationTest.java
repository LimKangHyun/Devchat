package project.api.domain.chat.chatroom.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import project.api.domain.chat.chatmessage.dao.ChatMessageRepository;
import project.api.domain.chat.chatmessage.entity.ChatMessage;
import project.api.domain.chat.chatmessage.entity.MessageType;
import project.api.domain.chat.chatmessage.mapper.ChatMessageMapper;
import project.api.domain.chat.chatroom.app.CheckpointReconstructor;
import project.api.domain.chat.chatroom.app.CheckpointWriter;
import project.api.domain.chat.chatroom.dao.ChatRoomCheckpointRepository;
import project.api.domain.chat.chatroom.dao.ChatRoomRedisRepository;
import project.api.domain.chat.chatroom.dao.ChatRoomRepository;
import project.api.domain.chat.chatroom.entity.ChatRoom;
import project.api.domain.chat.chatroom.entity.ChatRoomCheckpoint;
import project.api.domain.member.dao.MemberRepository;
import project.api.domain.member.entity.Member;
import project.api.global.config.TestRedisConfig;

import java.time.LocalDateTime;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * NOTE: CheckpointWriter는 이제 "createdAt이 watermark-lag-seconds 전보다 오래된
 * 메시지"만 안전 워터마크 후보로 본다 (기본 5초). 이 테스트 파일에서 "이미 안전하게
 * 집계되어야 하는" 메시지는 insertMessages(count, ago)로 충분히 과거 시각을 부여한다.
 * LocalDateTime.now()로 방금 넣은 메시지는 이 lag 안에 걸려 있어 즉시 집계되지
 * 않는 게 정상 동작이며, 그 자체를 검증하는 테스트를 별도로 둔다.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
@DisplayName("Checkpoint 통합 테스트")
class CheckpointIntegrationTest {

    @Container
    static MySQLContainer<?> mysql =
            new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
                    .withDatabaseName("testdb")
                    .withUsername("test")
                    .withPassword("test");

    @Container
    static GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired CheckpointWriter checkpointWriter;
    @Autowired CheckpointReconstructor reconstructor;
    @Autowired ChatRoomCheckpointRepository checkpointRepository;
    @Autowired ChatRoomRepository chatRoomRepository;
    @Autowired ChatMessageRepository chatMessageRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired ChatRoomRedisRepository chatRoomRedisRepository;
    @Autowired StringRedisTemplate redisTemplate;
    @Autowired ChatMessageMapper messageMapper;

    // CheckpointWriter와 동일한 프로퍼티를 읽어, "안전하게 지난 시각"을 lag와
    // 무관하게 항상 넉넉히 벌려준다. lag 설정값이 바뀌어도 테스트가 깨지지 않는다.
    @Value("${chat.checkpoint.watermark-lag-seconds:5}")
    private long watermarkLagSeconds;

    private ChatRoom room;
    private Member sender;

    @BeforeEach
    void setUp() {
        // @Transactional 없음 - 동시성 테스트에서 실제 커밋이 필요하므로 수동 정리
        chatMessageRepository.deleteAll();
        checkpointRepository.deleteAll();
        chatRoomRepository.deleteAll();
        memberRepository.deleteAll();
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();

        sender = memberRepository.save(
                Member.builder().username("tester").nickname("tester").profileImage("img").build());
        room = chatRoomRepository.save(ChatRoom.builder().name("테스트방").build());
    }

    /**
     * 워터마크 lag보다 충분히 이전 시각으로 메시지를 넣는다.
     * "이미 커밋이 끝난 지 오래된, 안전하게 집계되어야 할 메시지"를 만들 때 쓴다.
     * lag의 10배 여유를 둬서, 설정값이 바뀌거나 테스트 실행이 느려져도 흔들리지 않는다.
     */
    private void insertSafeMessages(int count) {
        LocalDateTime safeTime = LocalDateTime.now().minusSeconds(watermarkLagSeconds * 10 + 5);
        for (int i = 0; i < count; i++) {
            chatMessageRepository.save(
                    ChatMessage.builder()
                            .chatRoom(room)
                            .sender(sender)
                            .content("message-" + i)
                            .type(MessageType.TEXT)
                            .createdAt(safeTime)
                            .build()
            );
        }
    }

    /** 방금 생성된 것으로 취급되어야 하는, 워터마크 lag 안에 걸리는 메시지. */
    private void insertRecentMessages(int count) {
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < count; i++) {
            chatMessageRepository.save(
                    ChatMessage.builder()
                            .chatRoom(room)
                            .sender(sender)
                            .content("recent-" + i)
                            .type(MessageType.TEXT)
                            .createdAt(now)
                            .build()
            );
        }
    }

    @Test
    @DisplayName("체크포인트가 없으면 안전 워터마크 이전 메시지를 집계하여 초기화한다")
    void reconstruct_noCheckpoint_countsAll() {
        insertSafeMessages(50);

        Long result = checkpointWriter.reconstruct(room.getId());

        assertThat(result).isEqualTo(50L);
        ChatRoomCheckpoint cp = checkpointRepository.findById(room.getId()).orElseThrow();
        assertThat(cp.getCumulativeCount()).isEqualTo(50L);
        assertThat(cp.getSyncedMessageId())
                .isEqualTo(chatMessageRepository.findMaxIdByChatRoom_Id(room.getId()));
    }

    @Test
    @DisplayName("두 번째 호출은 체크포인트 이후 안전 구간만 집계한다")
    void reconstruct_secondCall_countsOnlyDelta() {
        insertSafeMessages(50);
        checkpointWriter.reconstruct(room.getId());
        Long firstSyncedId = checkpointRepository.findById(room.getId()).orElseThrow().getSyncedMessageId();

        insertSafeMessages(8);
        Long result = checkpointWriter.reconstruct(room.getId());

        assertThat(result).isEqualTo(58L);
        ChatRoomCheckpoint cp = checkpointRepository.findById(room.getId()).orElseThrow();
        assertThat(cp.getSyncedMessageId()).isGreaterThan(firstSyncedId);
    }

    @Test
    @DisplayName("새 메시지가 없으면 체크포인트가 변하지 않는다")
    void reconstruct_noNewMessages_unchanged() {
        insertSafeMessages(10);
        checkpointWriter.reconstruct(room.getId());
        ChatRoomCheckpoint before = checkpointRepository.findById(room.getId()).orElseThrow();
        Long beforeSyncedId = before.getSyncedMessageId();
        Long beforeCount = before.getCumulativeCount();

        Long result = checkpointWriter.reconstruct(room.getId());

        assertThat(result).isEqualTo(beforeCount);
        ChatRoomCheckpoint after = checkpointRepository.findById(room.getId()).orElseThrow();
        assertThat(after.getSyncedMessageId()).isEqualTo(beforeSyncedId);
        assertThat(after.getCumulativeCount()).isEqualTo(beforeCount);
    }

    @Test
    @DisplayName("메시지가 없는 방은 0을 반환한다")
    void reconstruct_emptyRoom_returnsZero() {
        Long result = checkpointWriter.reconstruct(room.getId());

        assertThat(result).isZero();
    }

    @Test
    @DisplayName("워터마크 lag 이내에 생성된 메시지는 아직 집계되지 않는다")
    void reconstruct_recentMessages_excludedByWatermarkLag() {
        insertSafeMessages(10);
        checkpointWriter.reconstruct(room.getId());

        // 방금 생성된 메시지 - createdAt이 threshold(now - lag)보다 나중이라
        // findMaxIdByRoomIdAndCreatedBefore 조회에 잡히지 않아야 한다.
        insertRecentMessages(5);
        Long result = checkpointWriter.reconstruct(room.getId());

        // 늦게 커밋될 수 있는 메시지를 위한 지연이 실제로 동작한다는 것을 증명한다.
        // 이 5개가 즉시 반영되면, 커밋 순서 역전 상황에서 영구 누락이 재발할 수 있다.
        assertThat(result).isEqualTo(10L);
    }

    @Test
    @DisplayName("lag 시간이 지나면 이전에 제외됐던 메시지도 다음 재구성에서 집계된다")
    void reconstruct_afterLagPasses_countsPreviouslyExcluded() throws InterruptedException {
        insertSafeMessages(10);
        checkpointWriter.reconstruct(room.getId());

        insertRecentMessages(5);
        checkpointWriter.reconstruct(room.getId()); // 이 시점엔 아직 lag 안 → 10 유지

        // lag 시간만큼 대기 후 재실행하면 그때는 안전 구간에 들어와 집계된다.
        // 실제 lag(기본 5초)만큼 기다리므로 이 테스트는 다소 느리다 - CI에서
        // watermark-lag-seconds를 짧게(예: 1초) 오버라이드해 실행 시간을 줄이는 것을 권장.
        Thread.sleep((watermarkLagSeconds + 2) * 1000);

        Long result = checkpointWriter.reconstruct(room.getId());

        assertThat(result).isEqualTo(15L);
    }

    @Test
    @DisplayName("100개 스레드가 동시에 재구성해도 카운트가 중복 누적되지 않는다")
    void reconstruct_concurrent_noDoubleCounting() throws InterruptedException {
        insertSafeMessages(100);

        int threadCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(32);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger errorCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    reconstructor.reconstruct(room.getId());
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean finished = doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();
        boolean terminated = executor.awaitTermination(10, TimeUnit.SECONDS);

        assertThat(finished).as("100개 스레드가 30초 내에 끝나지 않음").isTrue();
        assertThat(terminated).as("executor가 정상 종료되지 않음").isTrue();
        assertThat(errorCount.get()).isZero();
        // FOR UPDATE + single-flight로 중복 집계가 방지되어 정확히 100
        ChatRoomCheckpoint cp = checkpointRepository.findById(room.getId()).orElseThrow();
        assertThat(cp.getCumulativeCount()).isEqualTo(100L);
    }

    @Test
    @DisplayName("Redis가 비어 있어도 체크포인트만으로 정확한 값을 반환한다")
    void reconstruct_withoutRedis_returnsAccurateCount() {
        insertSafeMessages(30);
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();

        Long result = checkpointWriter.reconstruct(room.getId());

        assertThat(result).isEqualTo(30L);
    }

    @Test
    @DisplayName("재구성 후 Redis 캐시에도 동일한 값이 반영된다")
    void reconstruct_syncsToRedisCache() {
        insertSafeMessages(15);

        checkpointWriter.reconstruct(room.getId());

        assertThat(chatRoomRedisRepository.getSequence(room.getId())).isEqualTo(15L);
    }
}