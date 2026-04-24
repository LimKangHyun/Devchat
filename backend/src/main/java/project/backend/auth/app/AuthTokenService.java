package project.backend.auth.app;

import com.auth0.jwt.exceptions.TokenExpiredException;
import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.backend.auth.cache.TokenRefreshCachePort;
import project.backend.auth.dao.AuthTokenRepository;
import project.backend.auth.dto.MemberDetails;
import project.backend.auth.entity.AuthToken;
import project.backend.auth.jwt.JwtProvider;
import project.backend.global.exception.errorcode.TokenErrorCode;
import project.backend.global.exception.ex.CustomJwtException;
import project.backend.global.util.CookieUtils;
import project.backend.global.util.CryptUtils;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthTokenService {

    private final AuthTokenRepository authTokenRepository;
    private final JwtProvider jwtProvider;
    private final CryptUtils cryptUtils;
    private final TokenRefreshCachePort tokenRefreshCachePort;

    private static final long GRACE_PERIOD_SECONDS = 30;

    @Transactional
    public void saveToken(Long memberId, String refreshToken, String githubAccessToken) {
        String encryptedRefreshToken = cryptUtils.encrypt(refreshToken);
        String encryptedGithubToken = githubAccessToken != null
                ? cryptUtils.encrypt(githubAccessToken) : null;

        AuthToken authToken = authTokenRepository.findById(memberId)
                .orElse(null);

        if (authToken == null) {
            authTokenRepository.save(
                    AuthToken.of(memberId, encryptedRefreshToken, encryptedGithubToken));
        } else {
            authToken.updateRefreshToken(encryptedRefreshToken);
            authToken.updateGithubAccessToken(encryptedGithubToken);
        }
    }

    @Transactional
    public void refresh(String refreshToken, HttpServletResponse response) {

        Optional<String> cached = tokenRefreshCachePort.getNewTokenIfInGracePeriod(refreshToken);
        if (cached.isPresent()) {
            log.info("Grace Period 내 재발급 요청 - 캐시된 토큰 반환");
            CookieUtils.saveAccessTokenCookie(response, cached.get());
            return;
        }

        DecodedJWT decoded;
        try {
            decoded = jwtProvider.verifyRefreshToken(refreshToken);
        } catch (TokenExpiredException e) {
            throw new CustomJwtException(TokenErrorCode.EXPIRED_TOKEN);
        } catch (Exception e) {
            log.error("서명 검증 실패: {}", e.getMessage());
            throw new CustomJwtException(TokenErrorCode.INVALID_TOKEN);
        }

        Long memberId = Long.valueOf(decoded.getClaim("id").asString());

        AuthToken authToken = authTokenRepository.findById(memberId)
                .orElseThrow(() -> new CustomJwtException(TokenErrorCode.NOT_FOUND_TOKEN));

        String decryptedRefreshToken = cryptUtils.decrypt(authToken.getRefreshToken());
        if (!decryptedRefreshToken.equals(refreshToken)) {
            throw new CustomJwtException(TokenErrorCode.INVALID_TOKEN);
        }

        String newAccessToken = jwtProvider.regenerateAccessToken(refreshToken);

        String newRefreshToken = jwtProvider.generateTokenPair(
                new MemberDetails(memberId,
                        decoded.getClaim("username").asString(),
                        decoded.getClaim("nickname").asString(),
                        null)
        ).refreshToken();

        authToken.updateRefreshToken(cryptUtils.encrypt(newRefreshToken));

        tokenRefreshCachePort.saveWithGracePeriod(refreshToken, newAccessToken);

        CookieUtils.saveAccessTokenCookie(response, newAccessToken);
        CookieUtils.saveRefreshTokenCookie(response, newRefreshToken);

        log.info("Access Token 재발급 성공 - memberId: {}", memberId);
    }

    @Transactional
    public void logout(Long memberId) {
        authTokenRepository.deleteById(memberId);
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