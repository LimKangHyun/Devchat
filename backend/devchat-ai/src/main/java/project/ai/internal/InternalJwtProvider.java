package project.ai.internal;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class InternalJwtProvider {

    @Value("${internal.jwt.secret}")
    private String secret;

    public String issue() {
        return JWT.create()
                .withClaim("role", "INTERNAL_SERVICE")
                .withIssuedAt(new Date())
                .withExpiresAt(new Date(System.currentTimeMillis() + 60 * 1000)) // 1분
                .sign(Algorithm.HMAC256(secret));
    }
}