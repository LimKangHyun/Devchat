package project.api.domain.chat.chatroom.dao;

import java.util.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final DefaultRedisScript<Long> recoverAndIncrScript;
    private final DefaultRedisScript<List> getAndClearUpdatedRoomsScript;
    private final DefaultRedisScript<Long> setSequenceScript;
    private final DefaultRedisScript<Void> bulkSetSequenceScript;

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

    public Long recoverAndIncr(Long roomId, Long dbSeq) {
        return redisTemplate.execute(
                recoverAndIncrScript,
                List.of(String.format(ROOM_SEQUENCE_KEY, roomId), RANKING_ROOMS_KEY, UPDATED_ROOMS_KEY),
                String.valueOf(dbSeq),
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

    public void setSequence(Long roomId, Long sequence) {
        redisTemplate.execute(
                setSequenceScript,
                List.of(String.format(ROOM_SEQUENCE_KEY, roomId)),
                String.valueOf(sequence),
                String.valueOf(SEQUENCE_TTL_SEC)
        );
    }

    public void bulkSetSequences(Map<Long, Long> sequences) {
        if (sequences == null || sequences.isEmpty()) return;

        List<String> keys = new ArrayList<>();
        List<String> args = new ArrayList<>();

        sequences.forEach((roomId, seq) -> keys.add(String.format(ROOM_SEQUENCE_KEY, roomId)));
        sequences.forEach((roomId, seq) -> args.add(String.valueOf(seq)));
        sequences.forEach((roomId, seq) -> args.add(String.valueOf(SEQUENCE_TTL_SEC)));

        redisTemplate.execute(bulkSetSequenceScript, keys, args.toArray(new String[0]));
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