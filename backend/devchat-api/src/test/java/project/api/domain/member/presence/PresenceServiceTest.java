package project.api.domain.member.presence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PresenceService 온라인 상태")
class PresenceServiceTest {

    private final FakeStore store = new FakeStore();
    private final PresenceService service = new PresenceService(store);

    @Test
    @DisplayName("첫 접속이면 온라인으로 바뀌고, 마지막 세션이 끊기면 오프라인으로 바뀐다")
    void connectAndDisconnect() {
        assertThat(service.connect(1L, "alice", "s1")).isTrue();
        assertThat(service.onlineAmong(List.of(1L, 2L))).containsExactly(1L);

        assertThat(service.disconnect("s1")).hasValueSatisfying(m -> assertThat(m.username()).isEqualTo("alice"));
        assertThat(service.onlineAmong(List.of(1L))).isEmpty();
    }

    @Test
    @DisplayName("탭을 여러 개 열면 하나를 닫아도 온라인이 유지된다")
    void multipleTabs() {
        assertThat(service.connect(1L, "alice", "tab1")).isTrue();
        assertThat(service.connect(1L, "alice", "tab2")).isFalse(); // 이미 온라인

        assertThat(service.disconnect("tab1")).isEmpty();
        assertThat(service.onlineAmong(List.of(1L))).containsExactly(1L);

        assertThat(service.disconnect("tab2")).isPresent();
        assertThat(service.onlineAmong(List.of(1L))).isEmpty();
    }

    @Test
    @DisplayName("같은 세션의 끊김 이벤트가 두 번 와도 한 번만 처리한다")
    void duplicateDisconnectIgnored() {
        service.connect(1L, "alice", "s1");
        assertThat(service.disconnect("s1")).isPresent();
        assertThat(service.disconnect("s1")).isEmpty();
    }

    @Test
    @DisplayName("주기적 갱신이 이 서버의 세션 만료 시간을 연장한다")
    void refreshExtendsLocalSessions() {
        service.connect(1L, "alice", "s1");
        service.refreshLocalSessions();
        assertThat(store.refreshed).containsExactly("s1");
    }

    @Test
    @DisplayName("저장소 장애가 나도 친구 목록 조회는 실패하지 않는다")
    void storeFailureDoesNotBreakLookup() {
        store.failLookups = true;
        assertThat(service.onlineAmong(List.of(1L))).isEmpty();
    }

    /** Redis 대신 쓰는 메모리 저장소 */
    static class FakeStore implements PresenceStore {
        final Map<String, Long> sessions = new HashMap<>();
        final Set<String> refreshed = new HashSet<>();
        boolean failLookups;

        @Override
        public void addSession(Long memberId, String sessionId, Duration ttl) {
            sessions.put(sessionId, memberId);
        }

        @Override
        public void removeSession(Long memberId, String sessionId) {
            sessions.remove(sessionId);
        }

        @Override
        public void refreshSessions(Collection<String> sessionIds, Duration ttl) {
            refreshed.addAll(sessionIds);
        }

        @Override
        public Set<Long> onlineAmong(Collection<Long> memberIds) {
            if (failLookups) throw new IllegalStateException("redis down");
            return sessions.values().stream().filter(memberIds::contains).collect(Collectors.toSet());
        }

        final Set<String> visibleSessions = new HashSet<>();

        @Override
        public void markVisible(String sessionId, Duration ttl) {
            visibleSessions.add(sessionId);
        }

        @Override
        public void clearVisible(String sessionId) {
            visibleSessions.remove(sessionId);
        }

        @Override
        public Set<Long> visibleAmong(Collection<Long> memberIds) {
            if (failLookups) throw new IllegalStateException("redis down");
            return visibleSessions.stream().map(sessions::get)
                .filter(id -> id != null && memberIds.contains(id)).collect(Collectors.toSet());
        }
    }

    @Test
    @DisplayName("화면을 보고 있다고 알린 세션만 '보는 중'이고, 숨기거나 끊기면 빠진다")
    void visibility() {
        service.connect(1L, "alice", "phone");
        service.connect(1L, "alice", "laptop");
        assertThat(service.foregroundAmong(List.of(1L))).isEmpty(); // 연결만으로는 '보는 중'이 아니다

        service.updateVisibility("phone", true);
        assertThat(service.foregroundAmong(List.of(1L))).containsExactly(1L);

        service.updateVisibility("phone", false);
        assertThat(service.foregroundAmong(List.of(1L))).isEmpty();

        service.updateVisibility("laptop", true);
        service.disconnect("laptop");
        assertThat(service.foregroundAmong(List.of(1L))).isEmpty();
    }

    @Test
    @DisplayName("이 서버에 없는 세션의 화면 신호는 무시하고, 조회 실패 시엔 '안 보는 중'으로 본다(알림을 놓치지 않게)")
    void visibilityEdgeCases() {
        service.updateVisibility("unknown", true);
        assertThat(store.visibleSessions).isEmpty();

        store.failLookups = true;
        assertThat(service.foregroundAmong(List.of(1L))).isEmpty();
    }
}
