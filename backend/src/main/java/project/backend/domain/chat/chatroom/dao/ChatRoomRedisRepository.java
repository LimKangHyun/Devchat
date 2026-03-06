package project.backend.domain.chat.chatroom.dao;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Slf4j
@Repository
@RequiredArgsConstructor
public class ChatRoomRedisRepository {

    private static final String ROOM_LAST_MESSAGE_KEY = "room:lastMessageId:";

    private final StringRedisTemplate redisTemplate;

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

    public Long getLastMessageId(Long roomId) {

        String key = ROOM_LAST_MESSAGE_KEY + roomId;

        String value = redisTemplate.opsForValue().get(key);

        if (value == null) {
            return 0L;
        }

        return Long.parseLong(value);
    }

    public void updateLastMessageId(Long roomId, Long messageId) {
        try {
            redisTemplate.opsForValue()
                .set(ROOM_LAST_MESSAGE_KEY + roomId, messageId.toString());
        } catch (Exception e) {
            log.warn("Redis lastMessageId 갱신 실패. roomId: {}", roomId);
        }
    }

}