package project.api.auth.app;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.api.auth.dao.AuthTokenRepository;
import project.api.auth.dao.LoginSessionRepository;
import project.api.auth.entity.AuthToken;
import project.api.auth.dto.LoginSessionResponse;
import project.api.auth.entity.LoginSession;
import project.api.domain.member.dao.MemberRepository;
import project.api.global.exception.errorcode.LoginSessionErrorCode;
import project.api.global.exception.ex.LoginSessionException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LoginSessionService {

    private final LoginSessionRepository loginSessionRepository;
    private final MemberRepository memberRepository;
    private final RevokedSessionStore revokedSessionStore;
    private final ApplicationEventPublisher eventPublisher;
    private final AuthTokenRepository authTokenRepository;

    // 지금 세션을 맨 위로, 나머지는 마지막 사용 순
    @Transactional(readOnly = true)
    public List<LoginSessionResponse> list(Long memberId, String currentSessionId) {
        List<LoginSessionResponse> result = new ArrayList<>();
        for (LoginSession s : activeSessions(memberId)) {
            boolean current = s.getId().equals(currentSessionId);
            LoginSessionResponse response = LoginSessionResponse.of(s, current);
            if (current) result.add(0, response);
            else result.add(response);
        }
        return result;
    }

    @Transactional
    public void revoke(Long memberId, String sessionId, String currentSessionId) {
        if (sessionId.equals(currentSessionId)) {
            throw new LoginSessionException(LoginSessionErrorCode.CANNOT_REVOKE_CURRENT);
        }
        LocalDateTime now = LocalDateTime.now();
        LoginSession session = loginSessionRepository.findById(sessionId)
                .filter(s -> s.getMemberId().equals(memberId) && s.isActive(now))
                .orElseThrow(() -> new LoginSessionException(LoginSessionErrorCode.NOT_FOUND));
        session.revoke(now);
        revokedSessionStore.markRevoked(sessionId);
        publish(memberId, List.of(sessionId));
    }

    @Transactional
    public int revokeOthers(Long memberId, String currentSessionId) {
        LocalDateTime now = LocalDateTime.now();
        List<String> revoked = new ArrayList<>();
        for (LoginSession s : activeSessions(memberId)) {
            if (s.getId().equals(currentSessionId)) continue;
            s.revoke(now);
            revokedSessionStore.markRevoked(s.getId());
            revoked.add(s.getId());
        }
        // 세션으로 아직 안 옮긴 다른 기기의 옛 리프레시 토큰도 끊는다.
        // sid가 없으면 지금 기기가 그 옛 토큰을 쓰는 중이라 남긴다
        if (currentSessionId != null) {
            authTokenRepository.findById(memberId).ifPresent(AuthToken::clearRefreshToken);
        }
        if (!revoked.isEmpty()) publish(memberId, revoked);
        return revoked.size();
    }

    // 로그아웃: 지금 기기 세션만 끊는다. 옛 토큰이라 sid가 없으면 할 일 없음
    @Transactional
    public void revokeCurrent(String currentSessionId) {
        if (currentSessionId == null) return;
        loginSessionRepository.findById(currentSessionId).ifPresent(s -> {
            s.revoke(LocalDateTime.now());
            revokedSessionStore.markRevoked(s.getId());
        });
    }

    private List<LoginSession> activeSessions(Long memberId) {
        return loginSessionRepository
                .findByMemberIdAndRevokedAtIsNullAndExpiresAtAfterOrderByLastUsedAtDesc(
                        memberId, LocalDateTime.now());
    }

    private void publish(Long memberId, List<String> sessionIds) {
        memberRepository.findById(memberId).ifPresent(m ->
                eventPublisher.publishEvent(new SessionRevokedEvent(m.getUsername(), sessionIds)));
    }
}
