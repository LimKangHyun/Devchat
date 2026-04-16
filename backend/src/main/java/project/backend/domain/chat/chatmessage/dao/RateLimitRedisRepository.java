package project.backend.domain.chat.chatmessage.dao;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class RateLimitRedisRepository {

    private final StringRedisTemplate redisTemplate;

    private static final String USER_RATE_LIMIT_KEY = "rate:user:%d";
    private static final String INCR_SCRIPT =
            "local count = redis.call('INCR', KEYS[1]); " +
                    "if count == 1 then redis.call('EXPIRE', KEYS[1], 1) end; " +
                    "return count;";

    public Long increment(Long userId) {
        return redisTemplate.execute(
                new DefaultRedisScript<>(INCR_SCRIPT, Long.class),
                List.of(String.format(USER_RATE_LIMIT_KEY, userId))
        );
    }
}