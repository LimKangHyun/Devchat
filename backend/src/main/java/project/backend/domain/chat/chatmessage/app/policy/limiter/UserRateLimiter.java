package project.backend.domain.chat.chatmessage.app.policy.limiter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import project.backend.domain.chat.chatmessage.dao.RateLimitRedisRepository;
import project.backend.domain.chat.chatroom.dao.redis.ChatRoomRedisRepository;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class UserRateLimiter {

    private final RateLimitRedisRepository rateLimitRedisRepository;

    private static final int MAX_REQUESTS_PER_SECOND_NORMAL = 5;
    private static final int MAX_REQUESTS_PER_SECOND_STRICT = 2;

    private final ConcurrentHashMap<Long, AtomicInteger> userCounter = new ConcurrentHashMap<>();

    public UserRateLimiter(RateLimitRedisRepository rateLimitRedisRepository) {
        this.rateLimitRedisRepository = rateLimitRedisRepository;
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(userCounter::clear, 1, 1, TimeUnit.SECONDS);
    }

    public boolean allow(Long userId) {
        try {
            Long count = rateLimitRedisRepository.increment(userId);
            return count <= MAX_REQUESTS_PER_SECOND_NORMAL;
        } catch (Exception e) {
            log.warn("Redis Rate Limit 실패 - 메모리 기반으로 전환 userId={}", userId);
            return allowWithMemory(userId);
        }
    }

    private boolean allowWithMemory(Long userId) {
        userCounter.putIfAbsent(userId, new AtomicInteger(0));
        return userCounter.get(userId).incrementAndGet() <= MAX_REQUESTS_PER_SECOND_NORMAL;
    }

    public boolean allowStrict(Long userId) {
        try {
            Long count = rateLimitRedisRepository.increment(userId);
            return count <= MAX_REQUESTS_PER_SECOND_STRICT;
        } catch (Exception e) {
            log.warn("Redis Rate Limit 실패 - STRICT memory fallback userId={}", userId);
            return allowWithMemoryStrict(userId);
        }
    }

    private boolean allowWithMemoryStrict(Long userId) {
        userCounter.putIfAbsent(userId, new AtomicInteger(0));
        return userCounter.get(userId).incrementAndGet() <= MAX_REQUESTS_PER_SECOND_STRICT;
    }
}