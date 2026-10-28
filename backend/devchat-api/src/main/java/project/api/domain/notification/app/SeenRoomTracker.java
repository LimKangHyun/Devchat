package project.api.domain.notification.app;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Component;

/**
 * 회원이 최근에 본 방 (어느 기기에서든). 다른 기기의 알림 센터에 남은 그 방 알림을 지우는 데 쓴다.
 * 웹 푸시는 알림 없이 조용히 보낼 수 없어서(아이폰은 구독이 끊긴다), 기기가 앱을 열 때와
 * 다음 푸시를 받을 때 이 목록을 보고 이미 본 방의 알림을 정리한다.
 *
 * notify:seen:{memberId} = ZSET(roomId, 본 시각 ms), 하루 지난 기록은 버린다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeenRoomTracker {

    private static final String KEY = "notify:seen:";
    private static final Duration KEEP = Duration.ofDays(1);

    private final StringRedisTemplate redis;

    public record SeenRoom(Long roomId, long at) {}

    public void markSeen(Long memberId, Long roomId) {
        try {
            long now = System.currentTimeMillis();
            String key = KEY + memberId;
            redis.opsForZSet().add(key, String.valueOf(roomId), now);
            redis.opsForZSet().removeRangeByScore(key, 0, now - KEEP.toMillis());
            redis.expire(key, KEEP);
        } catch (Exception e) {
            log.warn("[알림 정리] 본 방 기록 실패 member={}: {}", memberId, e.getMessage());
        }
    }

    public List<SeenRoom> recentlySeen(Long memberId) {
        try {
            long since = System.currentTimeMillis() - KEEP.toMillis();
            Set<TypedTuple<String>> tuples = redis.opsForZSet().rangeByScoreWithScores(KEY + memberId, since, Double.MAX_VALUE);
            List<SeenRoom> result = new ArrayList<>();
            if (tuples == null) return result;
            for (TypedTuple<String> t : tuples) {
                if (t.getValue() == null || t.getScore() == null) continue;
                result.add(new SeenRoom(Long.valueOf(t.getValue()), t.getScore().longValue()));
            }
            return result;
        } catch (Exception e) {
            log.warn("[알림 정리] 본 방 조회 실패 member={}: {}", memberId, e.getMessage());
            return List.of();
        }
    }
}
