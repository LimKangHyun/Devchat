package project.backend.domain.chat.chatroom.dao;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Slf4j
@Repository
@RequiredArgsConstructor
public class ChatRoomRedisRepository {

    private static final String ROOM_SEQUENCE_KEY = "room:%d:sequence";

    private static final String UPDATED_ROOMS_KEY = "rooms:updated";
    private static final String RANKING_ROOMS_KEY = "rooms:ranking";

    private final StringRedisTemplate redisTemplate;

    public void handleMessageDelivery(Long roomId) {
        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            String seqKey = String.format(ROOM_SEQUENCE_KEY, roomId);
            String roomIdStr = String.valueOf(roomId);

            connection.stringCommands().incr(seqKey.getBytes());

            connection.zSetCommands().zAdd(
                    RANKING_ROOMS_KEY.getBytes(),
                    System.currentTimeMillis(),
                    roomIdStr.getBytes()
            );

            connection.setCommands().sAdd(
                    UPDATED_ROOMS_KEY.getBytes(),
                    roomIdStr.getBytes()
            );
            return null;
        });
    }

    public Long getSequence(Long roomId) {
        String key = String.format(ROOM_SEQUENCE_KEY, roomId);
        String value = redisTemplate.opsForValue().get(key);
        return value == null ? 0L : Long.parseLong(value);
    }

    public List<Long> getSequences(List<Long> roomIds) {
        if (roomIds == null || roomIds.isEmpty()) {
            return List.of();
        }
        List<Object> values = redisTemplate.executePipelined(
                (RedisCallback<Object>) connection -> {
                    for (Long roomId : roomIds) {
                        String key = String.format(ROOM_SEQUENCE_KEY, roomId);
                        connection.stringCommands().get(key.getBytes());
                    }
                    return null;
                });
        List<Long> result = new ArrayList<>(roomIds.size());

        for (Object val : values) {
            if (val == null) {
                result.add(0L);
            } else {
                result.add(Long.parseLong(new String((byte[]) val)));
            }
        }
        return result;
    }

    public List<Long> getSortedRoomIds(List<Long> roomIds) {
        if (roomIds == null || roomIds.isEmpty()) {
            return List.of();
        }
        Set<String> ranked = redisTemplate.opsForZSet()
                .reverseRange(RANKING_ROOMS_KEY, 0, roomIds.size() - 1);

        if (ranked == null || ranked.isEmpty()) {
            return new ArrayList<>(roomIds);
        }

        Set<Long> roomIdSet = new HashSet<>(roomIds);
        List<Long> sorted = new ArrayList<>(roomIds.size());
        for (String r : ranked) {
            Long id = Long.valueOf(r);
            if (roomIdSet.contains(id)) {
                sorted.add(id);
            }
        }

        Set<Long> added = new HashSet<>(sorted);
        for (Long id : roomIds) {
            if (!added.contains(id)) {
                sorted.add(id);
            }
        }
        return sorted;
    }
}