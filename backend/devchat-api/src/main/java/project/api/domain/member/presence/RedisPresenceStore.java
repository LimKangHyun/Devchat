package project.api.domain.member.presence;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * presence:member:{memberId} = 그 회원의 세션 id 집합
 * presence:session:{sessionId} = 회원 id (TTL. 접속한 서버가 주기적으로 연장)
 *
 * 서버가 죽어 끊김 이벤트를 못 받아도 세션 키가 만료되면 오프라인이 된다.
 * 집합에 남은 죽은 세션 id는 조회할 때 정리한다.
 */
@Repository
@RequiredArgsConstructor
public class RedisPresenceStore implements PresenceStore {

    private static final String MEMBER_KEY = "presence:member:";
    private static final String SESSION_KEY = "presence:session:";
    // 세션 집합 자체가 영영 남지 않도록 넉넉한 만료
    private static final Duration MEMBER_KEY_TTL = Duration.ofDays(1);

    private final StringRedisTemplate redis;

    @Override
    public void addSession(Long memberId, String sessionId, Duration ttl) {
        redis.opsForValue().set(SESSION_KEY + sessionId, String.valueOf(memberId), ttl);
        redis.opsForSet().add(MEMBER_KEY + memberId, sessionId);
        redis.expire(MEMBER_KEY + memberId, MEMBER_KEY_TTL);
    }

    @Override
    public void removeSession(Long memberId, String sessionId) {
        redis.delete(SESSION_KEY + sessionId);
        redis.opsForSet().remove(MEMBER_KEY + memberId, sessionId);
    }

    @Override
    public void refreshSessions(Collection<String> sessionIds, Duration ttl) {
        for (String sessionId : sessionIds) {
            redis.expire(SESSION_KEY + sessionId, ttl);
        }
    }

    @Override
    public Set<Long> onlineAmong(Collection<Long> memberIds) {
        Set<Long> online = new HashSet<>();
        for (Long memberId : new HashSet<>(memberIds)) {
            Set<String> sessionIds = redis.opsForSet().members(MEMBER_KEY + memberId);
            if (sessionIds == null || sessionIds.isEmpty()) continue;

            List<String> ids = new ArrayList<>(sessionIds);
            List<String> alive = redis.opsForValue().multiGet(ids.stream().map(id -> SESSION_KEY + id).toList());

            List<String> stale = new ArrayList<>();
            for (int i = 0; i < ids.size(); i++) {
                if (alive == null || alive.get(i) == null) stale.add(ids.get(i));
            }
            if (!stale.isEmpty()) {
                redis.opsForSet().remove(MEMBER_KEY + memberId, stale.toArray());
            }
            if (alive != null && alive.stream().anyMatch(Objects::nonNull)) {
                online.add(memberId);
            }
        }
        return online;
    }
}
