package project.api.global.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class UserRateLimiter {

    private static final int REFILL_RATE = 1;
    private static final int BUCKET_CAPACITY = 10;
    private static final int STRICT_COST = 2;
    private static final long BUCKET_TTL_MS = 60_000; // 1분 미사용 시 제거

    private static final int COOLDOWN_SECONDS = 5;

    private final RateLimitRedisClient rateLimitRedisClient;
    private final ConcurrentHashMap<Long, TokenBucket> memoryBuckets = new ConcurrentHashMap<>();

    public UserRateLimiter(RateLimitRedisClient rateLimitRedisClient) {
        this.rateLimitRedisClient = rateLimitRedisClient;
    }

    public boolean allow(Long userId) {
        log.info("allow 호출 userId={}", userId);
        try {
            boolean result =  rateLimitRedisClient.tryConsume(userId, 1, REFILL_RATE, BUCKET_CAPACITY);
            log.info("allow 결과 userId={} result={}", userId, result);
            return result;
        } catch (Exception e) {
            log.warn("Redis Rate Limit 실패 - 메모리 fallback userId={}", userId);
            return memoryBuckets
                .computeIfAbsent(userId, id -> new TokenBucket(BUCKET_CAPACITY, REFILL_RATE, COOLDOWN_SECONDS))
                .tryConsume(1);
        }
    }

    public boolean allowStrict(Long userId) {
        try {
            return rateLimitRedisClient.tryConsume(userId, STRICT_COST, REFILL_RATE, BUCKET_CAPACITY);
        } catch (Exception e) {
            log.warn("Redis Rate Limit 실패 - STRICT memory fallback userId={}", userId);
            return memoryBuckets
                .computeIfAbsent(userId, id -> new TokenBucket(BUCKET_CAPACITY, REFILL_RATE, COOLDOWN_SECONDS))
                .tryConsume(STRICT_COST);
        }
    }

    @Scheduled(fixedDelay = 60_000) // 1분마다 실행
    public void evictExpiredBuckets() {
        long now = System.currentTimeMillis();
        int before = memoryBuckets.size();
        memoryBuckets.entrySet().removeIf(entry -> entry.getValue().isExpired(now, BUCKET_TTL_MS));
        int removed = before - memoryBuckets.size();
        if (removed > 0) {
            log.debug("만료된 메모리 버킷 제거 - {}개", removed);
        }
    }
}