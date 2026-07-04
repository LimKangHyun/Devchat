package project.api.auth.cache;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisTokenRefreshCache implements TokenRefreshCachePort {

    private final StringRedisTemplate redisTemplate;
    private static final long GRACE_PERIOD_SECONDS = 30;
    private static final String GRACE_KEY_PREFIX = "grace:";

    @Override
    public void saveWithGracePeriod(String oldRefreshToken, String newAccessToken) {
        try {
            redisTemplate.opsForValue().set(
                    GRACE_KEY_PREFIX + oldRefreshToken,
                    newAccessToken,
                    GRACE_PERIOD_SECONDS,
                    TimeUnit.SECONDS
            );
        } catch (Exception e) {
            log.warn("Grace Period 캐시 저장 실패: {}", e.getMessage());
        }
    }

    @Override
    public Optional<String> getNewTokenIfInGracePeriod(String oldRefreshToken) {
        try {
            String cached = redisTemplate.opsForValue()
                    .get(GRACE_KEY_PREFIX + oldRefreshToken);
            return Optional.ofNullable(cached);
        } catch (Exception e) {
            log.warn("Grace Period 캐시 조회 실패: {}", e.getMessage());
            return Optional.empty();
        }
    }
}