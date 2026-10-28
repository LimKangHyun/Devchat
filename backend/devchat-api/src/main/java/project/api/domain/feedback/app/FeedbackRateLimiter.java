package project.api.domain.feedback.app;

import java.time.Duration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** 이슈 도배를 막는다: 한 사람당 1시간에 MAX_PER_HOUR번 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FeedbackRateLimiter {

    public static final int MAX_PER_HOUR = 5;
    private static final Duration WINDOW = Duration.ofHours(1);
    private static final String KEY = "feedback:count:%d";

    private final StringRedisTemplate redisTemplate;

    public boolean tryAcquire(Long memberId) {
        String key = String.format(KEY, memberId);
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) redisTemplate.expire(key, WINDOW);
            return count == null || count <= MAX_PER_HOUR;
        } catch (Exception e) {
            // Redis가 잠깐 안 되더라도 피드백은 받는다 (GitHub 자체 제한이 한 번 더 막는다)
            log.warn("피드백 횟수 확인 실패 - memberId={}", memberId);
            return true;
        }
    }
}
