package project.backend.auth.jwt;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.exceptions.SignatureVerificationException;
import com.auth0.jwt.exceptions.TokenExpiredException;
import com.auth0.jwt.interfaces.DecodedJWT;
import java.util.Date;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import project.backend.auth.dto.MemberDetails;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtProvider {

    public static final Long TOKEN_VALIDATION_SECOND = 10 * 60L;
    public static final Long REFRESH_TOKEN_VALIDATION_SECOND = 14 * 24 * 60 * 60L; // 2주

    @Value("${jwt.info.secret}")
    private String SECRET_KEY;

    private Algorithm getSignatureAlgorithm() {
        return Algorithm.HMAC256(SECRET_KEY);
    }

    public Token generateTokenPair(MemberDetails memberDetails) {
        Map<String, String> payload = Map.of(
                "username", memberDetails.getUsername(),
                "id", String.valueOf(memberDetails.getId()),
                "nickname", memberDetails.getNickname()
        );
        return new Token(generateAccessToken(payload), generateRefreshToken(payload));
    }

    private String generateAccessToken(Map<String, String> payload) {
        return doGenerateToken(TOKEN_VALIDATION_SECOND, payload);
    }

    private String generateRefreshToken(Map<String, String> payload) {
        return doGenerateToken(REFRESH_TOKEN_VALIDATION_SECOND, payload);
    }

    public String regenerateAccessToken(String refreshToken) {
        DecodedJWT decodedJWT = verifyRefreshToken(refreshToken);

        Map<String, String> payload = Map.of(
                "username", decodedJWT.getClaim("username").asString(),
                "id", decodedJWT.getClaim("id").asString(),
                "nickname", decodedJWT.getClaim("nickname").asString()
        );
        return generateAccessToken(payload);
    }

    public DecodedJWT verifyRefreshToken(String token) {
        return JWT.require(getSignatureAlgorithm())
                .build()
                .verify(token);
    }

    public JWTVerifier getJwtVerifier() {
        return JWT.require(getSignatureAlgorithm())
                .build();
    }

    public TokenStatus validateAccessToken(String token) {
        try {
            getJwtVerifier().verify(token);
            return TokenStatus.VALID;

        } catch (TokenExpiredException e) {
            log.warn("JWT 만료됨: {}", e.getMessage());
            return TokenStatus.EXPIRED;

        } catch (SignatureVerificationException e) {
            log.error("서명 오류: {}", e.getMessage());
            return TokenStatus.INVALID_SIGNATURE;

        } catch (JWTDecodeException e) {
            log.error("디코딩 오류: {}", e.getMessage());
            return TokenStatus.MALFORMED;

        } catch (JWTVerificationException e) {
            log.error("기타 검증 오류: {}", e.getMessage());
            return TokenStatus.UNKNOWN_ERROR;

        } catch (Exception e) {
            log.error("예상치 못한 오류: {}", e.getMessage());
            return TokenStatus.UNKNOWN_ERROR;
        }
    }

    public Authentication getAuthentication(String token) {
        DecodedJWT decodedJWT = getJwtVerifier().verify(token);

        Long id = Long.valueOf(decodedJWT.getClaim("id").asString());
        String username = decodedJWT.getClaim("username").asString();
        String nickname = decodedJWT.getClaim("nickname").asString();
        String profileImg = decodedJWT.getClaim("profileImg").asString();

        MemberDetails memberDetails = new MemberDetails(id, username, nickname, profileImg);
        return new UsernamePasswordAuthenticationToken(memberDetails, token,
                memberDetails.getAuthorities());
    }

    private String doGenerateToken(Long expiration, Map<String, String> payload) {
        long now = System.currentTimeMillis();
        return JWT.create()
                .withIssuedAt(new Date(now))
                .withExpiresAt(new Date(now + expiration * 1000))
                .withPayload(payload)
                .sign(getSignatureAlgorithm());
    }
}