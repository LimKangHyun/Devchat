package project.api.domain.chat.chatroom.dao;

import java.util.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

@Slf4j
@Repository
@RequiredArgsConstructor
public class ChatRoomRedisRepository {

    private static final String ROOM_SEQUENCE_KEY = "room:%d:sequence";
    private static final String UPDATED_ROOMS_KEY = "rooms:updated";
    private static final String RANKING_ROOMS_KEY = "rooms:ranking";

    private static final int MAX_RANKING_SIZE = 1000;
    private static final long SEQUENCE_TTL_SEC = 60 * 60 * 24 * 3;

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> genMessageSeqScript;
    private final DefaultRedisScript<List> getAndClearUpdatedRoomsScript;
    private final DefaultRedisScript<Long> setSequenceIfGreaterScript;

    public Long genMessageSeq(Long roomId) {
        return redisTemplate.execute(
                genMessageSeqScript,
                List.of(String.format(ROOM_SEQUENCE_KEY, roomId), RANKING_ROOMS_KEY, UPDATED_ROOMS_KEY),
                String.valueOf(SEQUENCE_TTL_SEC),
                String.valueOf(System.currentTimeMillis()),
                String.valueOf(roomId),
                String.valueOf(-MAX_RANKING_SIZE - 1)
        );
    }

    public List<Long> getSortedRoomIds(List<Long> roomIds) {
        if (roomIds == null || roomIds.isEmpty()) return List.of();

        Set<String> ranked = redisTemplate.opsForZSet()
                .reverseRange(RANKING_ROOMS_KEY, 0, MAX_RANKING_SIZE);

        if (ranked == null || ranked.isEmpty()) return new ArrayList<>(roomIds);

        Set<Long> roomIdSet = new HashSet<>(roomIds);
        List<Long> sorted = new ArrayList<>(roomIds.size());

        for (String r : ranked) {
            Long id = Long.valueOf(r);
            if (roomIdSet.contains(id)) sorted.add(id);
        }

        Set<Long> added = new HashSet<>(sorted);
        for (Long id : roomIds) {
            if (!added.contains(id)) sorted.add(id);
        }
        return sorted;
    }

    @SuppressWarnings("unchecked")
    public Set<String> getAndClearUpdatedRooms() {
        List<String> result = redisTemplate.execute(
                getAndClearUpdatedRoomsScript,
                List.of(UPDATED_ROOMS_KEY)
        );
        return new HashSet<>(result);
    }

    /**
     * 기존 값보다 클 때만 갱신한다 (역행 방지).
     * DB(checkpoint)에서 읽은 값을 캐시에 되쓸 때(read-through) 사용해,
     * 요청 간 fallback 여부가 갈리는 상황에서 stale한 값이 이미 최신인
     * 캐시를 덮어써 카운트가 순간적으로 줄어드는 것을 방지한다.
     */
    public void setSequenceIfGreater(Long roomId, Long value) {
        redisTemplate.execute(
                setSequenceIfGreaterScript,
                List.of(String.format(ROOM_SEQUENCE_KEY, roomId)),
                String.valueOf(value),
                String.valueOf(SEQUENCE_TTL_SEC)
        );
    }

    public void bulkSetSequencesIfGreater(Map<Long, Long> sequences) {
        if (sequences == null || sequences.isEmpty()) return;

        byte[] scriptBytes = setSequenceIfGreaterScript.getScriptAsString().getBytes();

        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            sequences.forEach((roomId, value) -> {
                connection.eval(
                        scriptBytes,
                        ReturnType.INTEGER,
                        1,
                        toBytes(String.format(ROOM_SEQUENCE_KEY, roomId)),
                        toBytes(String.valueOf(value)),
                        toBytes(String.valueOf(SEQUENCE_TTL_SEC))
                );
            });
            return null;
        });
    }

    public Long getSequence(Long roomId) {
        String value = redisTemplate.opsForValue().get(String.format(ROOM_SEQUENCE_KEY, roomId));
        return value == null ? -1L : Long.parseLong(value);
    }

    public List<Long> getSequences(List<Long> roomIds) {
        if (roomIds == null || roomIds.isEmpty()) return List.of();

        List<Object> values = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (Long roomId : roomIds) {
                connection.stringCommands().get(toBytes(String.format(ROOM_SEQUENCE_KEY, roomId)));
            }
            return null;
        });

        List<Long> result = new ArrayList<>(roomIds.size());
        for (Object val : values) {
            if (val == null) {
                result.add(null);
            } else {
                String strVal = val instanceof byte[] ? new String((byte[]) val) : val.toString();
                result.add(Long.parseLong(strVal));
            }
        }
        return result;
    }

    private byte[] toBytes(String key) {
        return redisTemplate.getStringSerializer().serialize(key);
    }
}