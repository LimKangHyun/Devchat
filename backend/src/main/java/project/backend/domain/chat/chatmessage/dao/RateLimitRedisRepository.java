package project.backend.domain.chat.chatmessage.dao;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class RateLimitRedisRepository {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String TOKEN_BUCKET_KEY = "token_bucket:%d";

    // 토큰 버킷 Lua 스크립트
    // KEYS[1] = 버킷 키
    // ARGV[1] = 현재 시간 (ms)
    // ARGV[2] = 초당 보충 토큰
    // ARGV[3] = 최대 용량
    // ARGV[4] = 소모 토큰
    // return 1 = 허용, 0 = 거부
    private static final String TOKEN_BUCKET_SCRIPT =
        "local key = KEYS[1] " +
            "local now = tonumber(ARGV[1]) " +
            "local refill_rate = tonumber(ARGV[2]) " +
            "local capacity = tonumber(ARGV[3]) " +
            "local cost = tonumber(ARGV[4]) " +

            "local bucket = redis.call('HMGET', key, 'tokens', 'last_refill') " +
            "local tokens = tonumber(bucket[1]) " +
            "local last_refill = tonumber(bucket[2]) " +

            // 첫 요청이면 버킷 초기화
            "if tokens == nil then " +
            "  tokens = capacity " +
            "  last_refill = now " +
            "end " +

            // 경과 시간만큼 토큰 보충
            "local elapsed = (now - last_refill) / 1000.0 " +
            "tokens = math.min(capacity, tokens + elapsed * refill_rate) " +

            // 토큰 부족 시 거부
            "if tokens < cost then " +
            "  redis.call('HMSET', key, 'tokens', tokens, 'last_refill', now) " +
            "  redis.call('EXPIRE', key, 60) " +
            "  return 0 " +
            "end " +

            // 토큰 차감 후 저장
            "tokens = tokens - cost " +
            "redis.call('HMSET', key, 'tokens', tokens, 'last_refill', now) " +
            "redis.call('EXPIRE', key, 60) " +
            "return 1";

    private static final DefaultRedisScript<Long> SCRIPT =
        new DefaultRedisScript<>(TOKEN_BUCKET_SCRIPT, Long.class);

    public boolean tryConsume(Long userId, int cost, int refillRate, int capacity) {
        long now = System.currentTimeMillis();
        Long result = redisTemplate.execute(
            SCRIPT,
            List.of(String.format(TOKEN_BUCKET_KEY, userId)),
            String.valueOf(now),
            String.valueOf(refillRate),
            String.valueOf(capacity),
            String.valueOf(cost)
        );
        return Long.valueOf(1L).equals(result);
    }
}