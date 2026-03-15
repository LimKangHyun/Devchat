package project.backend.domain.chat.chatroom.dao;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Slf4j
@Repository
@RequiredArgsConstructor
public class ChatRoomRedisRepository {

    private static final String ROOM_SEQUENCE_KEY = "room:sequence:";
    private static final String ROOM_RANKING_KEY = "room:ranking";

    private final StringRedisTemplate redisTemplate;

    public Long incrementSequence(Long roomId) {
        return redisTemplate.opsForValue().increment(ROOM_SEQUENCE_KEY + roomId);
    }

    public Long getSequence(Long roomId) {
        String value = redisTemplate.opsForValue().get(ROOM_SEQUENCE_KEY + roomId);
        return value == null ? 0L : Long.parseLong(value);
    }

    public List<Long> getSequences(List<Long> roomIds) {
        List<Object> values = redisTemplate.executePipelined(
            (RedisCallback<Object>) connection -> {
                roomIds.forEach(roomId -> {
                    String key = ROOM_SEQUENCE_KEY + roomId;
                    connection.stringCommands().get(key.getBytes());
                });
                return null;
            });
        return values.stream()
            .map(val -> val == null ? 0L : Long.parseLong(val.toString()))
            .toList();
    }

    public void updateRoomRanking(Long roomId) {
        redisTemplate.opsForZSet().add(
            ROOM_RANKING_KEY,
            String.valueOf(roomId),
            System.currentTimeMillis()
        );
    }

    // 방목록 조회 시 정렬된 roomId 목록 가져오기
    public List<Long> getSortedRoomIds(List<Long> roomIds) {
        Set<String> ranked = redisTemplate.opsForZSet()
            .reverseRange(ROOM_RANKING_KEY, 0, -1);

        if (ranked == null || ranked.isEmpty()) {
            return roomIds;
        }

        Set<Long> roomIdSet = new HashSet<>(roomIds);  // O(1) 탐색용

        List<Long> sorted = ranked.stream()
            .map(Long::valueOf)
            .filter(roomIdSet::contains)  // O(1)
            .collect(Collectors.toList());

        Set<Long> sortedSet = new HashSet<>(sorted);  // O(1) 탐색용
        roomIds.stream()
            .filter(id -> !sortedSet.contains(id))  // O(1)
            .forEach(sorted::add);

        return sorted;
    }

}