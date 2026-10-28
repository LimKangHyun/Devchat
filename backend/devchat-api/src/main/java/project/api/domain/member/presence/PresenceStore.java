package project.api.domain.member.presence;

import java.time.Duration;
import java.util.Collection;
import java.util.Set;

/** 웹소켓 세션 단위 접속 기록 저장소. 서버 여러 대가 같이 보도록 Redis에 둔다. */
public interface PresenceStore {

    void addSession(Long memberId, String sessionId, Duration ttl);

    void removeSession(Long memberId, String sessionId);

    /** 이 서버가 들고 있는 세션들의 만료 시간을 연장한다 */
    void refreshSessions(Collection<String> sessionIds, Duration ttl);

    /** 살아 있는 세션이 하나라도 있는 회원 id */
    Set<Long> onlineAmong(Collection<Long> memberIds);

    default boolean isOnline(Long memberId) {
        return onlineAmong(Set.of(memberId)).contains(memberId);
    }

    /** 이 세션이 지금 화면에 보이고 있다 (ttl 동안만 유효, 브라우저가 주기적으로 다시 알린다) */
    void markVisible(String sessionId, Duration ttl);

    void clearVisible(String sessionId);

    /** 화면을 보고 있는 세션이 하나라도 있는 회원 id */
    Set<Long> visibleAmong(Collection<Long> memberIds);
}
