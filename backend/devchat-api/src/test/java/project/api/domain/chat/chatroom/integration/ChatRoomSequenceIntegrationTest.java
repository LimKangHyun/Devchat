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
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import project.api.domain.chat.chatroom.app.ChatRoomSyncService;
import project.api.domain.chat.chatroom.dao.ChatRoomRedisRepository;
import project.api.domain.chat.chatroom.dao.ChatRoomRepository;
import project.api.domain.chat.chatroom.dao.FallbackSequenceRecoveryRepository;
import project.api.domain.chat.chatroom.entity.ChatRoom;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.LongStream;
import project.api.global.config.TestRedisConfig;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@Transactional
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
class ChatRoomSequenceIntegrationTest {

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
        // MySQL
        registry.add("spring.datasource.url",    mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        // Redis
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired ChatRoomSyncService chatRoomSyncService;
    @Autowired ChatRoomRedisRepository chatRoomRedisRepository;
    @Autowired ChatRoomRepository chatRoomRepository;
    @Autowired FallbackSequenceRecoveryRepository fallbackSequenceRecoveryRepository;
    @Autowired StringRedisTemplate redisTemplate;

    private static final String SEQ_KEY = "room:%d:sequence";

    @BeforeEach
    void clearRedis() {
        // DB는 @Transactional 롤백, Redis는 직접 삭제
        redisTemplate.delete(String.format(SEQ_KEY, 1L));
        redisTemplate.delete(String.format(SEQ_KEY, 2L));
    }

    @Test
    @DisplayName("Redis key가 있으면 INCR으로 순서대로 채번한다")
    void getOrRecoverSeq_whenKeyExists_incrementsSequentially() {
        // given
        chatRoomRedisRepository.setSequence(1L, 0L);

        // when & then
        assertThat(chatRoomSyncService.getOrRecoverSeq(1L)).isEqualTo(1L);
        assertThat(chatRoomSyncService.getOrRecoverSeq(1L)).isEqualTo(2L);
        assertThat(chatRoomSyncService.getOrRecoverSeq(1L)).isEqualTo(3L);
    }

    @Test
    @DisplayName("Redis key 없으면 ChatRoom.lastSequence 기반으로 복구하고 이어서 채번한다")
    void getOrRecoverSeq_whenKeyMissing_recoversFromDb() {
        // given - DB에 lastSequence=10인 채팅방, Redis key 없음
        ChatRoom room = ChatRoom.builder().name("테스트방").build();
        room.updateLastSequence(10L);
        chatRoomRepository.save(room);
        Long roomId = room.getId();

        // when
        Long seq = chatRoomSyncService.getOrRecoverSeq(roomId);

        // then - recoverAndIncr(dbSeq=10) → 11
        assertThat(seq).isEqualTo(11L);
        assertThat(chatRoomSyncService.getOrRecoverSeq(roomId)).isEqualTo(12L);
        assertThat(chatRoomSyncService.getOrRecoverSeq(roomId)).isEqualTo(13L);
    }

    @Test
    @DisplayName("100 스레드 동시 채번 시 sequence 중복/누락 없다")
    void getOrRecoverSeq_concurrent_noDuplicates() throws InterruptedException {
        // given
        ChatRoom room = ChatRoom.builder().name("동시성테스트방").build();
        chatRoomRepository.save(room);
        Long roomId = room.getId();
        chatRoomRedisRepository.setSequence(roomId, 0L);

        int threadCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(32);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch  = new CountDownLatch(threadCount);
        List<Long> results = new ArrayList<>(threadCount);
        AtomicInteger errorCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    Long seq = chatRoomSyncService.getOrRecoverSeq(roomId);
                    synchronized (results) { results.add(seq); }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        // then
        assertThat(errorCount.get()).isZero();
        assertThat(results).hasSize(threadCount);
        assertThat(results).doesNotHaveDuplicates();
        assertThat(results).containsExactlyInAnyOrderElementsOf(
            LongStream.rangeClosed(1, threadCount).boxed().toList()
        );
    }

    @Test
    @DisplayName("recoverSequences가 max(DB, Redis)로 복구하고 이후 채번이 이어진다")
    void recoverSequences_usesMaxOfDbAndRedis() {
        // given - fallback 구간 동안 DB sequence가 앞서 있는 상태
        ChatRoom room1 = ChatRoom.builder().name("방1").build();
        ChatRoom room2 = ChatRoom.builder().name("방2").build();
        room1.updateLastSequence(50L);
        room2.updateLastSequence(100L);
        chatRoomRepository.save(room1);
        chatRoomRepository.save(room2);

        fallbackSequenceRecoveryRepository.insertIgnore(room1.getId());
        fallbackSequenceRecoveryRepository.insertIgnore(room2.getId());

        // Redis: room1=30 (DB보다 뒤처짐), room2=없음
        chatRoomRedisRepository.setSequence(room1.getId(), 30L);

        // when
        chatRoomSyncService.recoverSequences();

        // then
        assertThat(chatRoomRedisRepository.getSequence(room1.getId())).isEqualTo(50L); // max(50, 30)
        assertThat(chatRoomRedisRepository.getSequence(room2.getId())).isEqualTo(100L); // max(100, 0)

        assertThat(chatRoomSyncService.getOrRecoverSeq(room1.getId())).isEqualTo(51L);
        assertThat(chatRoomSyncService.getOrRecoverSeq(room2.getId())).isEqualTo(101L);
    }
}