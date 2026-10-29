package project.api.auth.app;

import java.time.Duration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import project.api.auth.jwt.JwtProvider;

/** 끊긴 세션 표시. 유예 캐시보다 먼저 확인해서 끊긴 기기가 캐시된 토큰을 못 받게 한다 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RevokedSessionStore {

    private static final String KEY = "auth:revoked-session:%s";
    private static final Duration TTL = Duration.ofSeconds(JwtProvider.REFRESH_TOKEN_VALIDATION_SECOND);

    private final StringRedisTemplate redisTemplate;

    public void markRevoked(String sessionId) {
        try {
            redisTemplate.opsForValue().set(String.format(KEY, sessionId), "1", TTL);
        } catch (Exception e) {
            // DB 세션은 이미 끊겼으니 재발급은 막힌다. 유예 시간만 못 막는다
            log.warn("끊긴 세션 표시 실패 - sid={}", sessionId);
        }
    }

    public boolean isRevoked(String sessionId) {
        if (sessionId == null) return false;
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(String.format(KEY, sessionId)));
        } catch (Exception e) {
            log.warn("끊긴 세션 확인 실패 - sid={}", sessionId);
            return false;
        }
    }
}
