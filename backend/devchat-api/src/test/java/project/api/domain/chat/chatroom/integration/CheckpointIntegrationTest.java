package project.api.domain.chat.chatroom.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

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

    private ChatRoom room;
    private Member sender;

    @BeforeEach
    void setUp() {
        // @Transactional 없음 - 동시성 테스트에서 실제 커밋이 필요하므로 수동 정리
        chatMessageRepository.deleteAll();
        checkpointRepository.deleteAll();
        chatRoomRepository.deleteAll();
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();

        sender = memberRepository.save(
                Member.builder().username("tester").nickname("tester").profileImage("img").build());
        room = chatRoomRepository.save(ChatRoom.builder().name("테스트방").build());
    }

    private void insertMessages(int count) {
        for (int i = 0; i < count; i++) {
            chatMessageRepository.save(
                    ChatMessage.builder()
                            .chatRoom(room)
                            .sender(sender)
                            .content("message-" + i)
                            .type(MessageType.TEXT)
                            .createdAt(LocalDateTime.now())
                            .build()
            );
        }
    }

    @Test
    @DisplayName("체크포인트가 없으면 전체 메시지를 집계하여 초기화한다")
    void reconstruct_noCheckpoint_countsAll() {
        insertMessages(50);

        Long result = checkpointWriter.reconstruct(room.getId());

        assertThat(result).isEqualTo(50L);
        ChatRoomCheckpoint cp = checkpointRepository.findById(room.getId()).orElseThrow();
        assertThat(cp.getCumulativeCount()).isEqualTo(50L);
        assertThat(cp.getSyncedMessageId())
                .isEqualTo(chatMessageRepository.findMaxIdByChatRoom_Id(room.getId()));
    }

    @Test
    @DisplayName("두 번째 호출은 체크포인트 이후 구간만 집계한다")
    void reconstruct_secondCall_countsOnlyDelta() {
        insertMessages(50);
        checkpointWriter.reconstruct(room.getId());
        Long firstSyncedId = checkpointRepository.findById(room.getId()).orElseThrow().getSyncedMessageId();

        insertMessages(8);
        Long result = checkpointWriter.reconstruct(room.getId());

        assertThat(result).isEqualTo(58L);
        ChatRoomCheckpoint cp = checkpointRepository.findById(room.getId()).orElseThrow();
        assertThat(cp.getSyncedMessageId()).isGreaterThan(firstSyncedId);
    }

    @Test
    @DisplayName("새 메시지가 없으면 체크포인트가 변하지 않는다")
    void reconstruct_noNewMessages_unchanged() {
        insertMessages(10);
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
    @DisplayName("100개 스레드가 동시에 재구성해도 카운트가 중복 누적되지 않는다")
    void reconstruct_concurrent_noDoubleCounting() throws InterruptedException {
        insertMessages(100);

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
        doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(errorCount.get()).isZero();
        // FOR UPDATE + single-flight로 중복 집계가 방지되어 정확히 100
        ChatRoomCheckpoint cp = checkpointRepository.findById(room.getId()).orElseThrow();
        assertThat(cp.getCumulativeCount()).isEqualTo(100L);
    }

    @Test
    @DisplayName("Redis가 비어 있어도 체크포인트만으로 정확한 값을 반환한다")
    void reconstruct_withoutRedis_returnsAccurateCount() {
        insertMessages(30);
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();

        Long result = checkpointWriter.reconstruct(room.getId());

        assertThat(result).isEqualTo(30L);
    }

    @Test
    @DisplayName("재구성 후 Redis 캐시에도 동일한 값이 반영된다")
    void reconstruct_syncsToRedisCache() {
        insertMessages(15);

        checkpointWriter.reconstruct(room.getId());

        assertThat(chatRoomRedisRepository.getSequence(room.getId())).isEqualTo(15L);
    }
}