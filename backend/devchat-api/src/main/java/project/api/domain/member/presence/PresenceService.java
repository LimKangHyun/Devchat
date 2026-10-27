package project.api.domain.member.presence;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 친구 온라인 상태. 웹소켓 세션이 하나라도 살아 있으면 온라인이다.
 * 탭·기기를 여러 개 열어도 마지막 세션이 끊길 때만 오프라인이 된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PresenceService {

    // 갱신 주기(30초)의 3배. 서버가 죽으면 이 시간 안에 오프라인으로 바뀐다
    static final Duration SESSION_TTL = Duration.ofSeconds(90);

    public static final String ONLINE = "online";
    public static final String OFFLINE = "offline";

    private final PresenceStore store;

    // 이 서버에 붙은 세션. 끊김 이벤트에서 회원을 찾고, 만료 시간을 연장하는 데 쓴다
    private final Map<String, SessionOwner> localSessions = new ConcurrentHashMap<>();

    public record SessionOwner(Long id, String username) {}

    /** @return 이 접속으로 오프라인 → 온라인이 됐으면 true */
    public boolean connect(Long memberId, String username, String sessionId) {
        boolean wasOnline = store.isOnline(memberId);
        store.addSession(memberId, sessionId, SESSION_TTL);
        localSessions.put(sessionId, new SessionOwner(memberId, username));
        return !wasOnline;
    }

    /** @return 이 끊김으로 온라인 → 오프라인이 된 회원 (아직 다른 세션이 있으면 비어 있음) */
    public Optional<SessionOwner> disconnect(String sessionId) {
        SessionOwner member = localSessions.remove(sessionId);
        if (member == null) return Optional.empty();

        store.removeSession(member.id(), sessionId);
        return store.isOnline(member.id()) ? Optional.empty() : Optional.of(member);
    }

    public Set<Long> onlineAmong(Collection<Long> memberIds) {
        if (memberIds.isEmpty()) return Set.of();
        try {
            return store.onlineAmong(memberIds);
        } catch (Exception e) {
            // Redis 장애로 친구 목록까지 막히지 않게 한다 (모두 오프라인으로 보임)
            log.warn("[접속상태] 조회 실패: {}", e.getMessage());
            return Set.of();
        }
    }

    public String statusOf(Long memberId, Set<Long> online) {
        return online.contains(memberId) ? ONLINE : OFFLINE;
    }

    @Scheduled(fixedDelay = 30_000)
    public void refreshLocalSessions() {
        if (localSessions.isEmpty()) return;
        try {
            store.refreshSessions(localSessions.keySet(), SESSION_TTL);
        } catch (Exception e) {
            log.warn("[접속상태] 세션 만료 연장 실패: {}", e.getMessage());
        }
    }
}
