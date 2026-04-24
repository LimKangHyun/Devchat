package project.backend.auth.cache;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RedisTokenRefreshCache implements TokenRefreshCachePort {

    private final StringRedisTemplate redisTemplate;
    private static final long GRACE_PERIOD_SECONDS = 30;
    private static final String GRACE_KEY_PREFIX = "grace:";

    @Override
    public void saveWithGracePeriod(String oldRefreshToken, String newAccessToken) {
        redisTemplate.opsForValue().set(
                GRACE_KEY_PREFIX + oldRefreshToken,
                newAccessToken,
                GRACE_PERIOD_SECONDS,
                TimeUnit.SECONDS
        );
    }

    @Override
    public Optional<String> getNewTokenIfInGracePeriod(String oldRefreshToken) {
        String cached = redisTemplate.opsForValue()
                .get(GRACE_KEY_PREFIX + oldRefreshToken);
        return Optional.ofNullable(cached);
    }
}