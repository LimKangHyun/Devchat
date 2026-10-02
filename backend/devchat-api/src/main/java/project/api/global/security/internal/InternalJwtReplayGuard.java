package project.api.global.security.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class InternalJwtReplayGuard {

    private final StringRedisTemplate redisTemplate;

    public boolean consume(String jti) {
        Boolean result = redisTemplate.opsForValue()
            .setIfAbsent(
                "internal:jti:" + jti,
                "1",
                Duration.ofSeconds(60)
            );

        return Boolean.TRUE.equals(result);
    }
}