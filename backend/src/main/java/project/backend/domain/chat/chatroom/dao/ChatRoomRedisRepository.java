package project.backend.domain.chat.chatroom.dao;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.micrometer.core.instrument.MeterRegistry;
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
    private static final String MEMBER_PROFILE_KEY = "member:%d:profile";

    private static final int MAX_RANKING_SIZE = 1000;
    private static final long SEQUENCE_TTL_SEC = 60 * 60 * 24 * 3; // 3일
    private static final long PROFILE_TTL_SEC = 60 * 60 * 24; // 1일

    private final StringRedisTemplate redisTemplate;
    private final MeterRegistry meterRegistry;

    // 방 시퀀스 INCR -> INCR된 키의 ZSET TTL 초기화 (3일) -> 랭킹 상단 등록 -> 랭킹 MAX 사이즈만큼 자르기 -> ROOM SET 추가
    public Long handleMessageDelivery(Long roomId) {
        String script =
                "local prev = redis.call('GET', KEYS[1]); " +
                "local seq = redis.call('INCR', KEYS[1]); " +
                "redis.call('EXPIRE', KEYS[1], ARGV[1]); " +
                "redis.call('ZADD', KEYS[2], ARGV[2], ARGV[3]); " +
                "redis.call('ZREMRANGEBYRANK', KEYS[2], 0, ARGV[4]); " +
                "redis.call('SADD', KEYS[3], ARGV[3]); " +
                "if prev == false then return -1 end; " +
                "return seq;";

        String seqKey = String.format(ROOM_SEQUENCE_KEY, roomId);
        String roomIdStr = String.valueOf(roomId);

        try {
            return redisTemplate.execute(
                    new DefaultRedisScript<>(script, Long.class),
                    List.of(seqKey, RANKING_ROOMS_KEY, UPDATED_ROOMS_KEY),
                    String.valueOf(SEQUENCE_TTL_SEC),
                    String.valueOf(System.currentTimeMillis()),
                    roomIdStr,
                    String.valueOf(-MAX_RANKING_SIZE - 1)
            );
        } catch (Exception e) {
            log.error("Lua 스크립트 실행 오류 - roomId: {}", roomId, e);
            meterRegistry.counter("redis.fallback", "method", "handleMessageDelivery").increment();
            return null;
        }
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

    public Set<String> getAndClearUpdatedRooms() {
        String script =
                "local members = redis.call('SMEMBERS', KEYS[1]); " +
                        "redis.call('DEL', KEYS[1]); " +
                        "return members;";

        List<String> result = redisTemplate.execute(
                new DefaultRedisScript<>(script, List.class),
                List.of(UPDATED_ROOMS_KEY)
        );

        return result != null ? new HashSet<>(result) : Set.of();
    }

    public void setSequence(Long roomId, Long sequence) {
        String script =
                "local cur = redis.call('GET', KEYS[1]); " +
                "if cur == false or tonumber(cur) < tonumber(ARGV[1]) then " +
                "  redis.call('SET', KEYS[1], ARGV[1]); " +
                "  redis.call('EXPIRE', KEYS[1], ARGV[2]); " +
                "end; ";

        String key = String.format(ROOM_SEQUENCE_KEY, roomId);
        redisTemplate.execute(
                new DefaultRedisScript<>(script, Long.class),
                List.of(key),
                String.valueOf(sequence),
                String.valueOf(SEQUENCE_TTL_SEC)
        );
    }

    public Long getSequence(Long roomId) {
        String key = String.format(ROOM_SEQUENCE_KEY, roomId);
        String value = redisTemplate.opsForValue().get(key);
        return value == null ? 0L : Long.parseLong(value);
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
                result.add(0L);
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

    public String getProfileImage(Long memberId) {
        String key = String.format(MEMBER_PROFILE_KEY, memberId);
        return redisTemplate.opsForValue().get(key);
    }

    public void setProfileImage(Long memberId, String profileImg) {
        String key = String.format(MEMBER_PROFILE_KEY, memberId);
        redisTemplate.opsForValue().set(key, profileImg, PROFILE_TTL_SEC, java.util.concurrent.TimeUnit.SECONDS);
    }

    public void deleteProfileImage(Long memberId) {
        String key = String.format(MEMBER_PROFILE_KEY, memberId);
        redisTemplate.delete(key);
    }
}