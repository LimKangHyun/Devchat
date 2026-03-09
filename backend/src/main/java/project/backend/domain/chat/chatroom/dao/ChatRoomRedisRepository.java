package project.backend.domain.chat.chatroom.dao;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Repository;

@Slf4j
@Repository
@RequiredArgsConstructor
public class ChatRoomRedisRepository {

    private static final String ROOM_LAST_MESSAGE_KEY = "room:lastMessageId:";

    private final StringRedisTemplate redisTemplate;

    private final ConcurrentHashMap<Long, AtomicLong> lastMessageMap = new ConcurrentHashMap<>();

    public List<Long> getLastMessageIds(List<Long> roomIds) {

        List<Object> values = redisTemplate.executePipelined(
            (RedisCallback<Object>) connection -> {
                roomIds.forEach(roomId -> {
                    String key = ROOM_LAST_MESSAGE_KEY + roomId;
                    connection.stringCommands().get(key.getBytes());
                });
                return null;
            });

        return values.stream()
            .map(val -> val == null ? 0L : Long.parseLong(val.toString()))
            .toList();
    }

    public void updateLastMessageId(Long roomId, Long messageId) {
        lastMessageMap
            .computeIfAbsent(roomId, k -> new AtomicLong(0))
            .updateAndGet(prev -> Math.max(prev, messageId));
    }

    @Scheduled(fixedDelay = 300)
    public void flushToRedis() {

        if (lastMessageMap.isEmpty()) {
            return;
        }

        Map<Long, Long> snapshot = new HashMap<>();

        lastMessageMap.forEach((roomId, atomic) -> {
            long val = atomic.get();
            if (val > 0) {
                snapshot.put(roomId, val);
            }
        });

        if (snapshot.isEmpty()) {
            return;
        }

        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {

            snapshot.forEach((roomId, messageId) -> {

                String key = ROOM_LAST_MESSAGE_KEY + roomId;

                connection.stringCommands().set(
                    key.getBytes(StandardCharsets.UTF_8),
                    messageId.toString().getBytes(StandardCharsets.UTF_8)
                );

            });

            return null;
        });
    }

    public Long getLastMessageId(Long roomId) {

        String key = ROOM_LAST_MESSAGE_KEY + roomId;

        String value = redisTemplate.opsForValue().get(key);

        if (value == null) {
            return 0L;
        }

        return Long.parseLong(value);
    }

}