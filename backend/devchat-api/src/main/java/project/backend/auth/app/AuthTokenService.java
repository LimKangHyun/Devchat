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
import project.backend.auth.jwt.Token;
import project.backend.domain.member.entity.Member;
import project.backend.global.exception.errorcode.TokenErrorCode;
import project.backend.global.exception.ex.CustomJwtException;
import project.backend.global.util.CookieUtils;
import project.backend.global.util.CryptUtils;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthTokenService {

    private final AuthTokenRepository authTokenRepository;
    private final JwtProvider jwtProvider;
    private final CryptUtils cryptUtils;
    private final TokenRefreshCachePort tokenRefreshCachePort;

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
        if (handleGracePeriod(refreshToken, response)) return;

        DecodedJWT decoded = verifyAndValidateRefreshToken(refreshToken);
        Long memberId = Long.valueOf(decoded.getClaim("id").asString());

        AuthToken authToken = getAuthToken(memberId);
        validateStoredRefreshToken(authToken, refreshToken);

        MemberDetails memberDetails = extractMemberDetails(memberId, decoded);
        String newAccessToken = jwtProvider.regenerateAccessToken(refreshToken);
        String newRefreshToken = jwtProvider.generateTokenPair(memberDetails).refreshToken();

        authToken.updateRefreshToken(cryptUtils.encrypt(newRefreshToken));
        tokenRefreshCachePort.saveWithGracePeriod(refreshToken, newAccessToken);
        CookieUtils.saveAccessTokenCookie(response, newAccessToken);
        CookieUtils.saveRefreshTokenCookie(response, newRefreshToken);

        log.info("Access Token 재발급 성공 - memberId: {}", memberId);
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

    private AuthToken getAuthToken(Long memberId) {
        return authTokenRepository.findById(memberId)
                .orElseThrow(() -> new CustomJwtException(TokenErrorCode.NOT_FOUND_TOKEN));
    }

    private void validateStoredRefreshToken(AuthToken authToken, String refreshToken) {
        String decrypted = cryptUtils.decrypt(authToken.getRefreshToken());
        if (!decrypted.equals(refreshToken)) {
            throw new CustomJwtException(TokenErrorCode.INVALID_TOKEN);
        }
    }

    private MemberDetails extractMemberDetails(Long memberId, DecodedJWT decoded) {
        return new MemberDetails(
                memberId,
                decoded.getClaim("username").asString(),
                decoded.getClaim("nickname").asString(),
                null
        );
    }

    @Transactional
    public void reissueTokensForNicknameChange(Member member, HttpServletResponse response) {
        MemberDetails memberDetails = new MemberDetails(
                member.getId(),
                member.getUsername(),
                member.getNickname(),
                null
        );

        AuthToken authToken = authTokenRepository.findById(member.getId())
                .orElseThrow(() -> new CustomJwtException(TokenErrorCode.NOT_FOUND_TOKEN));

        issueAndSaveTokens(memberDetails, authToken, response);
    }

    private void issueAndSaveTokens(MemberDetails memberDetails, AuthToken authToken,
                                    HttpServletResponse response) {
        Token newToken = jwtProvider.generateTokenPair(memberDetails);
        authToken.updateRefreshToken(cryptUtils.encrypt(newToken.refreshToken()));
        CookieUtils.saveAccessTokenCookie(response, newToken.accessToken());
        CookieUtils.saveRefreshTokenCookie(response, newToken.refreshToken());
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