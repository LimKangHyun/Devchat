package project.api.global.security.internal;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class InternalJwtValidator {

    @Value("${internal.jwt.secret}")
    private String secret;

    public DecodedJWT validate(String token) {
        try {
            return JWT.require(Algorithm.HMAC256(secret))
                    .withClaim("role", "INTERNAL_SERVICE")
                    .build()
                    .verify(token);
        } catch (JWTVerificationException e) {
            log.warn("Internal JWT 검증 실패: {}", e.getMessage());
            return null;
        }
    }
}