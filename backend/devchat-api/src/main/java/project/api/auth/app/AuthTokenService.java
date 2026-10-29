package project.api.auth.app;

import com.auth0.jwt.exceptions.TokenExpiredException;
import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import project.api.global.util.CookieUtils;
import project.api.global.util.CryptUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthTokenService {

    // 세션을 만들 때 잠깐 넣는 값. 토큰을 발급한 뒤 바로 진짜 해시로 바꾼다
    private static final String PENDING_HASH = "0".repeat(64);

    private final AuthTokenRepository authTokenRepository;
    private final JwtProvider jwtProvider;
    private final CryptUtils cryptUtils;
    private final TokenRefreshCachePort tokenRefreshCachePort;
    private final LoginSessionRepository loginSessionRepository;
    private final MemberRepository memberRepository;
    private final RevokedSessionStore revokedSessionStore;

    // 리프레시 토큰은 해시만 저장한다 (SHA-256 hex 64자)
    public static String hash(String refreshToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(refreshToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 쓸 수 없습니다", e);
        }
    }

    // 로그인: 기기 세션을 만들고 토큰을 쿠키에 담는다
    @Transactional
    public void login(MemberDetails member, String userAgent, String githubAccessToken,
                      HttpServletResponse response) {
        Token token = issueWithNewSession(member, userAgent);
        saveGithubAccessToken(member.getId(), githubAccessToken);
        saveCookies(response, token);
        log.info("로그인 세션 생성 - memberId: {}", member.getId());
    }

    // 깃허브 토큰이 null이면(폼 로그인) 기존 값을 그대로 둔다
    private void saveGithubAccessToken(Long memberId, String githubAccessToken) {
        String encrypted = githubAccessToken != null ? cryptUtils.encrypt(githubAccessToken) : null;
        Optional<AuthToken> existing = authTokenRepository.findById(memberId);

        if (existing.isEmpty()) {
            authTokenRepository.save(AuthToken.of(memberId, null, encrypted));
        } else if (encrypted != null) {
            existing.get().updateGithubAccessToken(encrypted);
        }
    }

    @Transactional
    public void refresh(String refreshToken, String userAgent, HttpServletResponse response) {
        DecodedJWT decoded = verifyAndValidateRefreshToken(refreshToken);
        Long memberId = Long.valueOf(decoded.getClaim("id").asString());
        String sessionId = decoded.getClaim("sid").asString();

        // 끊긴 세션은 유예 캐시보다 먼저 막는다
        if (sessionId != null && revokedSessionStore.isRevoked(sessionId)) {
            throw new CustomJwtException(TokenErrorCode.INVALID_TOKEN);
        }

        if (handleGracePeriod(refreshToken, response)) return;

        Token newToken = sessionId != null
                ? rotateSession(sessionId, memberId, refreshToken)
                : migrateLegacyToken(memberId, refreshToken, userAgent);

        tokenRefreshCachePort.saveWithGracePeriod(refreshToken, newToken.accessToken());
        saveCookies(response, newToken);

        log.info("Access Token 재발급 성공 - memberId: {}", memberId);
    }

    // 같은 세션으로 새 토큰을 발급하고 해시를 바꾼다
    private Token rotateSession(String sessionId, Long memberId, String refreshToken) {
        LocalDateTime now = LocalDateTime.now();
        LoginSession session = loginSessionRepository.findById(sessionId)
                .filter(s -> s.getMemberId().equals(memberId))
                .filter(s -> s.isActive(now))
                .filter(s -> s.getRefreshTokenHash().equals(hash(refreshToken)))
                .orElseThrow(() -> new CustomJwtException(TokenErrorCode.INVALID_TOKEN));

        Token token = jwtProvider.generateTokenPair(loadMemberDetails(memberId, sessionId), sessionId);
        session.rotate(hash(token.refreshToken()), now, expiresAt(now));
        return token;
    }

    // sid 없는 옛 토큰: AuthToken과 맞으면 새 세션으로 옮긴다
    private Token migrateLegacyToken(Long memberId, String refreshToken, String userAgent) {
        AuthToken authToken = authTokenRepository.findById(memberId)
                .orElseThrow(() -> new CustomJwtException(TokenErrorCode.INVALID_TOKEN));
        String stored = authToken.getRefreshToken();
        if (stored == null || !cryptUtils.decrypt(stored).equals(refreshToken)) {
            throw new CustomJwtException(TokenErrorCode.INVALID_TOKEN);
        }

        Token token = issueWithNewSession(loadMemberDetails(memberId, null), userAgent);
        authToken.clearRefreshToken();
        log.info("옛 토큰을 세션으로 옮김 - memberId: {}", memberId);
        return token;
    }

    // 세션을 만들고 그 sid로 토큰을 발급한다
    private Token issueWithNewSession(MemberDetails member, String userAgent) {
        LocalDateTime now = LocalDateTime.now();
        LoginSession session = LoginSession.create(member.getId(), PENDING_HASH,
                DeviceNameParser.parse(userAgent), now, expiresAt(now));
        Token token = jwtProvider.generateTokenPair(member, session.getId());
        session.rotate(hash(token.refreshToken()), now, expiresAt(now));
        loginSessionRepository.save(session);
        return token;
    }

    // 닉네임과 아이디는 옛 토큰이 아니라 DB에서 읽는다
    private MemberDetails loadMemberDetails(Long memberId, String sessionId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomJwtException(TokenErrorCode.INVALID_TOKEN));
        return new MemberDetails(member.getId(), member.getUsername(), member.getNickname(),
                member.getProfileImage(), sessionId);
    }

    private LocalDateTime expiresAt(LocalDateTime now) {
        return now.plusSeconds(JwtProvider.REFRESH_TOKEN_VALIDATION_SECOND);
    }

    private boolean handleGracePeriod(String refreshToken, HttpServletResponse response) {
        Optional<String> cached = tokenRefreshCachePort.getNewTokenIfInGracePeriod(refreshToken);
        if (cached.isPresent()) {
            log.info("Grace Period 내 재발급 요청 - 캐시된 토큰 반환");
            CookieUtils.saveAccessTokenCookie(response, cached.get());
            return true;
        }
        return false;
    }

    private DecodedJWT verifyAndValidateRefreshToken(String refreshToken) {
        try {
            return jwtProvider.verifyRefreshToken(refreshToken);
        } catch (TokenExpiredException e) {
            throw new CustomJwtException(TokenErrorCode.EXPIRED_TOKEN);
        } catch (Exception e) {
            log.error("서명 검증 실패: {}", e.getMessage());
            throw new CustomJwtException(TokenErrorCode.INVALID_TOKEN);
        }
    }

    // 닉네임이 바뀌면 지금 기기의 세션만 새 토큰으로 바꾼다.
    // 옛 토큰(sid 없음)은 다음 재발급 때 세션으로 옮겨지면서 DB 닉네임을 받는다
    @Transactional
    public void reissueTokensForNicknameChange(Member member, String sessionId,
                                               HttpServletResponse response) {
        if (sessionId == null) return;

        LocalDateTime now = LocalDateTime.now();
        LoginSession session = loginSessionRepository.findById(sessionId)
                .filter(s -> s.getMemberId().equals(member.getId()))
                .filter(s -> s.isActive(now))
                .orElseThrow(() -> new CustomJwtException(TokenErrorCode.INVALID_TOKEN));

        MemberDetails memberDetails = new MemberDetails(member.getId(), member.getUsername(),
                member.getNickname(), member.getProfileImage(), sessionId);
        Token token = jwtProvider.generateTokenPair(memberDetails, sessionId);
        session.rotate(hash(token.refreshToken()), now, expiresAt(now));
        saveCookies(response, token);
    }

    private void saveCookies(HttpServletResponse response, Token token) {
        CookieUtils.saveAccessTokenCookie(response, token.accessToken());
        CookieUtils.saveRefreshTokenCookie(response, token.refreshToken());
    }

    // 옛 토큰(sid 없음)으로 로그아웃할 때 저장된 리프레시 토큰만 지운다
    @Transactional
    public void clearLegacyRefreshToken(Long memberId) {
        authTokenRepository.findById(memberId).ifPresent(AuthToken::clearRefreshToken);
    }

    public String getGithubAccessToken(Long memberId) {
        AuthToken authToken = authTokenRepository.findById(memberId)
                .orElseThrow(() -> new CustomJwtException(TokenErrorCode.NOT_FOUND_TOKEN));

        if (authToken.getGithubAccessToken() == null) {
            throw new CustomJwtException(TokenErrorCode.NOT_FOUND_TOKEN);
        }

        return cryptUtils.decrypt(authToken.getGithubAccessToken());
    }
}
