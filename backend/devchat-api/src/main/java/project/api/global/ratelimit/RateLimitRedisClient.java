package project.api.global.ratelimit;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class RateLimitRedisClient {

    private static final String TOKEN_BUCKET_KEY = "token_bucket:%d";
    private static final int COOLDOWN_SECONDS = 5;

    private final RedisTemplate<String, String> redisTemplate;
    private final DefaultRedisScript<Long> tokenBucketScript;

    @PostConstruct
    public void init() {
        tokenBucketScript.setScriptSource(
                new ResourceScriptSource(new ClassPathResource("scripts/token_bucket.lua"))
        );
        tokenBucketScript.setResultType(Long.class);
    }

    public boolean tryConsume(Long userId, int cost, int refillRate, int capacity) {
        long now = System.currentTimeMillis();
        Long result = redisTemplate.execute(
                tokenBucketScript,
                List.of(String.format(TOKEN_BUCKET_KEY, userId)),
                String.valueOf(now),
                String.valueOf(refillRate),
                String.valueOf(capacity),
                String.valueOf(cost),
                String.valueOf(COOLDOWN_SECONDS)
        );
        return Long.valueOf(1L).equals(result);
    }
}