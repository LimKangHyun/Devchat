package project.backend.global.security.internal;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class InternalJwtValidator {

    @Value("${internal.jwt.secret}")
    private String secret;

    public boolean validate(String token) {
        try {
            JWT.require(Algorithm.HMAC256(secret))
                    .withClaim("role", "INTERNAL_SERVICE")
                    .build()
                    .verify(token);
            return true;
        } catch (JWTVerificationException e) {
            log.warn("Internal JWT 검증 실패: {}", e.getMessage());
            return false;
        }
    }
}