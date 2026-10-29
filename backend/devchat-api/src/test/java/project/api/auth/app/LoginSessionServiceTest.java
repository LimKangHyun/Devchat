package project.api.auth.app;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import project.api.auth.dao.LoginSessionRepository;
import project.api.auth.dto.LoginSessionResponse;
import project.api.auth.entity.LoginSession;
import project.api.domain.member.dao.MemberRepository;
import project.api.domain.member.entity.Member;
import project.api.global.exception.errorcode.LoginSessionErrorCode;
import project.api.global.exception.ex.LoginSessionException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LoginSessionServiceTest {

    @Mock LoginSessionRepository repository;
    @Mock MemberRepository memberRepository;
    @Mock RevokedSessionStore revokedSessionStore;
    @Mock ApplicationEventPublisher eventPublisher;

    LoginSessionService service;

    @BeforeEach
    void setUp() {
        service = new LoginSessionService(repository, memberRepository, revokedSessionStore, eventPublisher);
    }

    private LoginSession session(String id, Long memberId, LocalDateTime lastUsedAt) {
        LocalDateTime now = LocalDateTime.now();
        LoginSession s = LoginSession.create(memberId, "hash", "Chrome · Windows",
                now.minusDays(1), now.plusDays(13));
        ReflectionTestUtils.setField(s, "id", id);
        ReflectionTestUtils.setField(s, "lastUsedAt", lastUsedAt);
        return s;
    }

    @Test
    void list_marksCurrentAndPutsItFirst() {
        LocalDateTime now = LocalDateTime.now();
        LoginSession s1 = session("s-1", 1L, now.minusMinutes(1));
        LoginSession s2 = session("s-2", 1L, now.minusHours(1));
        given(repository.findByMemberIdAndRevokedAtIsNullAndExpiresAtAfterOrderByLastUsedAtDesc(
                any(), any())).willReturn(List.of(s1, s2));

        List<LoginSessionResponse> result = service.list(1L, "s-2");

        assertThat(result).extracting(LoginSessionResponse::id).containsExactly("s-2", "s-1");
        assertThat(result.get(0).current()).isTrue();
        assertThat(result.get(1).current()).isFalse();
    }

    @Test
    void revoke_marksRevokedAndPublishesEvent() {
        LoginSession target = session("s-2", 1L, LocalDateTime.now());
        Member member = Member.builder().id(1L).username("user1").build();
        given(repository.findById("s-2")).willReturn(Optional.of(target));
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));

        service.revoke(1L, "s-2", "s-1");

        assertThat(target.getRevokedAt()).isNotNull();
        verify(revokedSessionStore).markRevoked("s-2");
        ArgumentCaptor<SessionRevokedEvent> captor = ArgumentCaptor.forClass(SessionRevokedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().username()).isEqualTo("user1");
        assertThat(captor.getValue().sessionIds()).containsExactly("s-2");
    }

    @Test
    void revoke_otherMembersSession_notFound() {
        LoginSession other = session("s-9", 2L, LocalDateTime.now());
        given(repository.findById("s-9")).willReturn(Optional.of(other));

        assertThatThrownBy(() -> service.revoke(1L, "s-9", "s-1"))
                .isInstanceOfSatisfying(LoginSessionException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(LoginSessionErrorCode.NOT_FOUND));
        verify(revokedSessionStore, never()).markRevoked(anyString());
    }

    @Test
    void revoke_currentSession_rejected() {
        assertThatThrownBy(() -> service.revoke(1L, "s-1", "s-1"))
                .isInstanceOfSatisfying(LoginSessionException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(LoginSessionErrorCode.CANNOT_REVOKE_CURRENT));
        verify(revokedSessionStore, never()).markRevoked(anyString());
    }

    @Test
    void revokeOthers_keepsCurrent() {
        LocalDateTime now = LocalDateTime.now();
        LoginSession s1 = session("s-1", 1L, now);
        LoginSession s2 = session("s-2", 1L, now);
        LoginSession s3 = session("s-3", 1L, now);
        Member member = Member.builder().id(1L).username("user1").build();
        given(repository.findByMemberIdAndRevokedAtIsNullAndExpiresAtAfterOrderByLastUsedAtDesc(
                any(), any())).willReturn(List.of(s1, s2, s3));
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));

        int count = service.revokeOthers(1L, "s-1");

        assertThat(count).isEqualTo(2);
        assertThat(s1.getRevokedAt()).isNull();
        assertThat(s2.getRevokedAt()).isNotNull();
        assertThat(s3.getRevokedAt()).isNotNull();
        verify(revokedSessionStore).markRevoked("s-2");
        verify(revokedSessionStore).markRevoked("s-3");
        verify(revokedSessionStore, never()).markRevoked("s-1");
    }
}
