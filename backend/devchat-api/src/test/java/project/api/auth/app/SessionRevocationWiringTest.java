package project.api.auth.app;

import com.auth0.jwt.JWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import project.api.auth.api.LogoutController;
import project.api.auth.cache.TokenRefreshCachePort;
import project.api.auth.dao.AuthTokenRepository;
import project.api.auth.dao.LoginSessionRepository;
import project.api.auth.dto.MemberDetails;
import project.api.auth.entity.AuthToken;
import project.api.auth.entity.LoginSession;
import project.api.auth.jwt.JwtProvider;
import project.api.domain.imagefile.ImageFileService;
import project.api.domain.member.app.MemberService;
import project.api.domain.member.app.ProfileImageCache;
import project.api.domain.member.dao.MemberRepository;
import project.api.domain.member.dto.MemberInfoUpdateRequest;
import project.api.domain.member.dto.PasswordChangeRequest;
import project.api.domain.member.entity.Member;
import project.api.domain.member.entity.ProviderType;
import project.api.global.exception.ex.MemberException;
import project.api.global.util.CryptUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 로그아웃, 비밀번호 변경, 닉네임 변경이 세션을 맞게 다루는지 확인한다 */
@ExtendWith(MockitoExtension.class)
class SessionRevocationWiringTest {

    @Mock LoginSessionService loginSessionService;
    @Mock MemberRepository memberRepository;
    @Mock ImageFileService imageFileService;
    @Mock PasswordEncoder passwordEncoder;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock ProfileImageCache profileImageCache;
    @Mock AuthTokenRepository authTokenRepository;
    @Mock CryptUtils cryptUtils;
    @Mock TokenRefreshCachePort cache;
    @Mock LoginSessionRepository loginSessionRepository;
    @Mock RevokedSessionStore revokedSessionStore;

    AuthTokenService authTokenService;
    MemberService memberService;
    MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        JwtProvider jwtProvider = new JwtProvider();
        ReflectionTestUtils.setField(jwtProvider, "SECRET_KEY", "test-secret-key");
        authTokenService = new AuthTokenService(authTokenRepository, jwtProvider, cryptUtils, cache,
                loginSessionRepository, memberRepository, revokedSessionStore);
        memberService = new MemberService(memberRepository, imageFileService, authTokenService,
                passwordEncoder, eventPublisher, profileImageCache, loginSessionService);
        response = new MockHttpServletResponse();
    }

    private Authentication auth(String sessionId) {
        MemberDetails details = new MemberDetails(1L, "user1", "옛닉", "img.png", sessionId);
        return new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
    }

    @Test
    void logout_revokesOnlyCurrentSession() {
        LogoutController controller = new LogoutController(loginSessionService, authTokenService);
        MemberDetails details = new MemberDetails(1L, "user1", "옛닉", "img.png", "s-1");

        controller.logout(details, response);

        verify(loginSessionService).revokeCurrent("s-1");
        verify(loginSessionService, never()).revokeOthers(any(), any());
        verify(authTokenRepository, never()).findById(any());
        assertThat(response.getStatus()).isEqualTo(204);
    }

    @Test
    void logout_legacyToken_clearsStoredRefreshToken() {
        LogoutController controller = new LogoutController(loginSessionService, authTokenService);
        MemberDetails details = new MemberDetails(1L, "user1", "옛닉", "img.png", null);
        AuthToken authToken = AuthToken.of(1L, "enc-refresh", "enc-github");
        given(authTokenRepository.findById(1L)).willReturn(Optional.of(authToken));

        controller.logout(details, response);

        assertThat(authToken.getRefreshToken()).isNull();
        assertThat(authToken.getGithubAccessToken()).isEqualTo("enc-github");
        verify(loginSessionService, never()).revokeCurrent(any());
        assertThat(response.getStatus()).isEqualTo(204);
    }

    @Test
    void passwordChange_revokesOtherSessions() {
        Member member = Member.builder().id(1L).username("user1").nickname("옛닉")
                .password("stored").provider(ProviderType.LOCAL).profileImage("img.png").build();
        PasswordChangeRequest request = new PasswordChangeRequest();
        ReflectionTestUtils.setField(request, "currentPassword", "old-pw");
        ReflectionTestUtils.setField(request, "newPassword", "new-pw");
        ReflectionTestUtils.setField(request, "confirmPassword", "new-pw");
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(passwordEncoder.matches("old-pw", "stored")).willReturn(true);
        given(passwordEncoder.matches("new-pw", "stored")).willReturn(false);
        given(passwordEncoder.encode(anyString())).willReturn("encoded");

        memberService.updatePassword(auth("s-1"), request);

        verify(loginSessionService).revokeOthers(1L, "s-1");
    }

    @Test
    void passwordChange_wrongCurrentPassword_revokesNothing() {
        Member member = Member.builder().id(1L).username("user1").nickname("옛닉")
                .password("stored").provider(ProviderType.LOCAL).profileImage("img.png").build();
        PasswordChangeRequest request = new PasswordChangeRequest();
        ReflectionTestUtils.setField(request, "currentPassword", "wrong-pw");
        ReflectionTestUtils.setField(request, "newPassword", "new-pw");
        ReflectionTestUtils.setField(request, "confirmPassword", "new-pw");
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(passwordEncoder.matches("wrong-pw", "stored")).willReturn(false);

        assertThatThrownBy(() -> memberService.updatePassword(auth("s-1"), request))
                .isInstanceOf(MemberException.class);

        verify(loginSessionService, never()).revokeOthers(any(), any());
    }

    @Test
    void nicknameChange_legacyToken_noCookiesNoSessionTouched() {
        Member member = Member.builder().id(1L).username("user1").nickname("옛닉")
                .profileImage("img.png").build();
        MemberInfoUpdateRequest request = new MemberInfoUpdateRequest();
        ReflectionTestUtils.setField(request, "nickname", "새닉");
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));

        memberService.updateMemberInfo(auth(null), request, null, response);

        assertThat(member.getNickname()).isEqualTo("새닉");
        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
        verifyNoInteractions(loginSessionRepository);
    }

    @Test
    void nicknameChange_rotatesCurrentSessionWithNewNickname() {
        Member member = Member.builder().id(1L).username("user1").nickname("옛닉")
                .profileImage("img.png").build();
        LocalDateTime now = LocalDateTime.now();
        LoginSession session = LoginSession.create(1L, AuthTokenService.hash("old-refresh"),
                "Chrome · Windows", now.minusDays(1), now.plusDays(13));
        MemberInfoUpdateRequest request = new MemberInfoUpdateRequest();
        ReflectionTestUtils.setField(request, "nickname", "새닉");
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(loginSessionRepository.findById(session.getId())).willReturn(Optional.of(session));

        memberService.updateMemberInfo(auth(session.getId()), request, null, response);

        String access = cookie("accessToken");
        assertThat(JWT.decode(access).getClaim("nickname").asString()).isEqualTo("새닉");
        assertThat(JWT.decode(access).getClaim("sid").asString()).isEqualTo(session.getId());
        assertThat(session.getRefreshTokenHash()).isEqualTo(AuthTokenService.hash(cookie("refreshToken")));
    }

    private String cookie(String name) {
        return response.getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(c -> c.startsWith(name + "="))
                .findFirst()
                .map(c -> c.substring(name.length() + 1, c.indexOf(';')))
                .orElseThrow();
    }
}
