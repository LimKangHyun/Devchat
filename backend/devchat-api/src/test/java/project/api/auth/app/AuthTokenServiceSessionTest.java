package project.api.auth.app;

import com.auth0.jwt.JWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import project.api.auth.cache.TokenRefreshCachePort;
import project.api.auth.dao.AuthTokenRepository;
import project.api.auth.dao.LoginSessionRepository;
import project.api.auth.dto.MemberDetails;
import project.api.auth.entity.AuthToken;
import project.api.auth.entity.LoginSession;
import project.api.auth.jwt.JwtProvider;
import project.api.auth.jwt.Token;
import project.api.domain.member.dao.MemberRepository;
import project.api.domain.member.entity.Member;
import project.api.global.exception.errorcode.TokenErrorCode;
import project.api.global.exception.ex.CustomJwtException;
import project.api.global.util.CryptUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthTokenServiceSessionTest {

    private static final String CHROME_WINDOWS =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    @Mock AuthTokenRepository authTokenRepository;
    @Mock CryptUtils cryptUtils;
    @Mock TokenRefreshCachePort cache;
    @Mock LoginSessionRepository loginSessionRepository;
    @Mock MemberRepository memberRepository;
    @Mock RevokedSessionStore revokedSessionStore;

    JwtProvider jwtProvider;
    AuthTokenService service;
    MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        jwtProvider = new JwtProvider();
        ReflectionTestUtils.setField(jwtProvider, "SECRET_KEY", "test-secret-key");
        service = new AuthTokenService(authTokenRepository, jwtProvider, cryptUtils, cache,
                loginSessionRepository, memberRepository, revokedSessionStore);
        response = new MockHttpServletResponse();
    }

    private Member member(String nickname) {
        return Member.builder().id(1L).username("user1").nickname(nickname)
                .profileImage("img.png").build();
    }

    private LoginSession activeSession(String refreshToken) {
        LocalDateTime now = LocalDateTime.now();
        return LoginSession.create(1L, AuthTokenService.hash(refreshToken), "Chrome · Windows",
                now.minusDays(1), now.plusDays(13));
    }

    private String refreshCookie() {
        List<String> cookies = response.getHeaders(HttpHeaders.SET_COOKIE);
        return cookies.stream().filter(c -> c.startsWith("refreshToken=")).findFirst()
                .map(c -> c.substring("refreshToken=".length(), c.indexOf(';')))
                .orElseThrow();
    }

    private String accessCookie() {
        List<String> cookies = response.getHeaders(HttpHeaders.SET_COOKIE);
        return cookies.stream().filter(c -> c.startsWith("accessToken=")).findFirst()
                .map(c -> c.substring("accessToken=".length(), c.indexOf(';')))
                .orElseThrow();
    }

    @Test
    void hash_isSha256Hex() {
        assertThat(AuthTokenService.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void login_createsSessionAndKeepsGithubTokenWhenNull() {
        AuthToken existing = AuthToken.of(1L, null, "enc-github");
        MemberDetails details = new MemberDetails(1L, "user1", "닉네임", null);
        given(authTokenRepository.findById(1L)).willReturn(Optional.of(existing));

        service.login(details, CHROME_WINDOWS, null, response);

        ArgumentCaptor<LoginSession> captor = ArgumentCaptor.forClass(LoginSession.class);
        verify(loginSessionRepository).save(captor.capture());
        LoginSession saved = captor.getValue();
        assertThat(saved.getDeviceName()).isEqualTo("Chrome · Windows");
        assertThat(saved.getMemberId()).isEqualTo(1L);
        assertThat(existing.getGithubAccessToken()).isEqualTo("enc-github");

        String refresh = refreshCookie();
        assertThat(saved.getRefreshTokenHash()).isEqualTo(AuthTokenService.hash(refresh));
        assertThat(JWT.decode(refresh).getClaim("sid").asString()).isEqualTo(saved.getId());
        assertThat(JWT.decode(accessCookie()).getClaim("sid").asString()).isEqualTo(saved.getId());
    }

    @Test
    void login_createsAuthTokenRowWhenMissing() {
        MemberDetails details = new MemberDetails(1L, "user1", "닉네임", null);
        given(authTokenRepository.findById(1L)).willReturn(Optional.empty());
        given(cryptUtils.encrypt("gh-token")).willReturn("enc-gh");

        service.login(details, CHROME_WINDOWS, "gh-token", response);

        ArgumentCaptor<AuthToken> captor = ArgumentCaptor.forClass(AuthToken.class);
        verify(authTokenRepository).save(captor.capture());
        assertThat(captor.getValue().getRefreshToken()).isNull();
        assertThat(captor.getValue().getGithubAccessToken()).isEqualTo("enc-gh");
    }

    @Test
    void refresh_rotatesSameSession() {
        MemberDetails oldDetails = new MemberDetails(1L, "user1", "옛닉네임", null);
        LoginSession session = activeSession("placeholder");
        String sid = session.getId();
        String oldRefresh = jwtProvider.generateTokenPair(oldDetails, sid).refreshToken();
        session.rotate(AuthTokenService.hash(oldRefresh), LocalDateTime.now(),
                LocalDateTime.now().plusDays(14));
        String oldHash = session.getRefreshTokenHash();
        Member dbMember = member("새닉네임");
        given(revokedSessionStore.isRevoked(sid)).willReturn(false);
        given(cache.getNewTokenIfInGracePeriod(oldRefresh)).willReturn(Optional.empty());
        given(loginSessionRepository.findById(sid)).willReturn(Optional.of(session));
        given(memberRepository.findById(1L)).willReturn(Optional.of(dbMember));

        service.refresh(oldRefresh, CHROME_WINDOWS, response);

        String newRefresh = refreshCookie();
        String newAccess = accessCookie();
        assertThat(JWT.decode(newRefresh).getClaim("sid").asString()).isEqualTo(sid);
        assertThat(JWT.decode(newAccess).getClaim("sid").asString()).isEqualTo(sid);
        assertThat(JWT.decode(newAccess).getClaim("nickname").asString()).isEqualTo("새닉네임");
        assertThat(session.getRefreshTokenHash())
                .isNotEqualTo(oldHash)
                .isEqualTo(AuthTokenService.hash(newRefresh));
        verify(cache).saveWithGracePeriod(oldRefresh, newAccess);
    }

    @Test
    void refresh_hashMismatch_rejected() {
        MemberDetails details = new MemberDetails(1L, "user1", "닉네임", null);
        LoginSession session = activeSession("other-token");
        String sid = session.getId();
        String refresh = jwtProvider.generateTokenPair(details, sid).refreshToken();
        given(revokedSessionStore.isRevoked(sid)).willReturn(false);
        given(cache.getNewTokenIfInGracePeriod(refresh)).willReturn(Optional.empty());
        given(loginSessionRepository.findById(sid)).willReturn(Optional.of(session));

        assertThatThrownBy(() -> service.refresh(refresh, CHROME_WINDOWS, response))
                .isInstanceOf(CustomJwtException.class)
                .extracting("errorCode").isEqualTo(TokenErrorCode.INVALID_TOKEN);
    }

    @Test
    void refresh_revokedSessionInGrace_rejected() {
        MemberDetails details = new MemberDetails(1L, "user1", "닉네임", null);
        String refresh = jwtProvider.generateTokenPair(details, "s-1").refreshToken();
        given(revokedSessionStore.isRevoked("s-1")).willReturn(true);

        assertThatThrownBy(() -> service.refresh(refresh, CHROME_WINDOWS, response))
                .isInstanceOf(CustomJwtException.class)
                .extracting("errorCode").isEqualTo(TokenErrorCode.INVALID_TOKEN);
        verify(cache, never()).getNewTokenIfInGracePeriod(any());
    }

    @Test
    void refresh_withinGracePeriod_returnsCachedToken() {
        MemberDetails details = new MemberDetails(1L, "user1", "닉네임", null);
        String refresh = jwtProvider.generateTokenPair(details, "s-1").refreshToken();
        given(revokedSessionStore.isRevoked("s-1")).willReturn(false);
        given(cache.getNewTokenIfInGracePeriod(refresh)).willReturn(Optional.of("cached-access"));

        service.refresh(refresh, CHROME_WINDOWS, response);

        assertThat(accessCookie()).isEqualTo("cached-access");
        verify(loginSessionRepository, never()).findById(any());
    }

    @Test
    void refresh_legacyTokenMatches_migratesToSession() {
        MemberDetails details = new MemberDetails(1L, "user1", "옛닉네임", null);
        String legacyRefresh = jwtProvider.generateTokenPair(details, null).refreshToken();
        AuthToken authToken = AuthToken.of(1L, "enc-legacy", "enc-github");
        Member dbMember = member("새닉네임");
        given(cache.getNewTokenIfInGracePeriod(legacyRefresh)).willReturn(Optional.empty());
        given(authTokenRepository.findById(1L)).willReturn(Optional.of(authToken));
        given(cryptUtils.decrypt("enc-legacy")).willReturn(legacyRefresh);
        given(memberRepository.findById(1L)).willReturn(Optional.of(dbMember));

        service.refresh(legacyRefresh, CHROME_WINDOWS, response);

        ArgumentCaptor<LoginSession> captor = ArgumentCaptor.forClass(LoginSession.class);
        verify(loginSessionRepository).save(captor.capture());
        LoginSession saved = captor.getValue();
        assertThat(saved.getDeviceName()).isEqualTo("Chrome · Windows");
        assertThat(authToken.getRefreshToken()).isNull();
        assertThat(authToken.getGithubAccessToken()).isEqualTo("enc-github");

        String newRefresh = refreshCookie();
        assertThat(JWT.decode(newRefresh).getClaim("sid").asString()).isEqualTo(saved.getId());
        assertThat(JWT.decode(accessCookie()).getClaim("nickname").asString()).isEqualTo("새닉네임");
        assertThat(saved.getRefreshTokenHash()).isEqualTo(AuthTokenService.hash(newRefresh));
    }

    @Test
    void refresh_legacyTokenMismatch_rejected() {
        MemberDetails details = new MemberDetails(1L, "user1", "닉네임", null);
        String legacyRefresh = jwtProvider.generateTokenPair(details, null).refreshToken();
        AuthToken authToken = AuthToken.of(1L, "enc-other", null);
        given(cache.getNewTokenIfInGracePeriod(legacyRefresh)).willReturn(Optional.empty());
        given(authTokenRepository.findById(1L)).willReturn(Optional.of(authToken));
        given(cryptUtils.decrypt("enc-other")).willReturn("some-other-token");

        assertThatThrownBy(() -> service.refresh(legacyRefresh, CHROME_WINDOWS, response))
                .isInstanceOf(CustomJwtException.class)
                .extracting("errorCode").isEqualTo(TokenErrorCode.INVALID_TOKEN);
        verify(loginSessionRepository, never()).save(any());
    }

    @Test
    void refresh_legacyTokenAlreadyMigrated_rejected() {
        MemberDetails details = new MemberDetails(1L, "user1", "닉네임", null);
        String legacyRefresh = jwtProvider.generateTokenPair(details, null).refreshToken();
        AuthToken authToken = AuthToken.of(1L, null, null);
        given(cache.getNewTokenIfInGracePeriod(legacyRefresh)).willReturn(Optional.empty());
        given(authTokenRepository.findById(1L)).willReturn(Optional.of(authToken));

        assertThatThrownBy(() -> service.refresh(legacyRefresh, CHROME_WINDOWS, response))
                .isInstanceOf(CustomJwtException.class)
                .extracting("errorCode").isEqualTo(TokenErrorCode.INVALID_TOKEN);
        verify(loginSessionRepository, never()).save(any());
    }
}
