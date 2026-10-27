package project.ai.stream.aireview;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * AI 리뷰 메시지 단위의 생존 신호.
 * 처리를 시작하면 heartbeat:ai-review:{recordId}를 TTL 60초로 만들고, 처리하는 동안 30초마다 TTL을 늘린다.
 * Worker가 죽으면 갱신이 멈춰 키가 만료되고, 재처리 스케줄러는 키가 있으면 아직 처리 중으로 보고 건드리지 않는다.
 * 경과 시간만으로는 "죽은 작업"과 "오래 걸리는 작업"을 구분할 수 없어서 둔다.
 */
@Slf4j
@Component
public class AiReviewHeartbeat {

    static final Duration TTL = Duration.ofSeconds(60);
    static final long INTERVAL_SECONDS = 30;
    private static final String KEY_PREFIX = "heartbeat:ai-review:";

    private final StringRedisTemplate redisTemplate;
    private final ScheduledExecutorService scheduler;
    private final Map<String, ScheduledFuture<?>> running = new ConcurrentHashMap<>();

    public AiReviewHeartbeat(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "ai-review-heartbeat");
            t.setDaemon(true);
            return t;
        });
    }

    public static String key(String recordId) {
        return KEY_PREFIX + recordId;
    }

    public void start(String recordId) {
        String key = key(recordId);
        redisTemplate.opsForValue().set(key, "1", TTL);
        ScheduledFuture<?> renewal = scheduler.scheduleAtFixedRate(() -> {
            try {
                redisTemplate.expire(key, TTL);
            } catch (Exception e) {
                log.warn("Heartbeat 갱신 실패 - recordId={}", recordId, e);
            }
        }, INTERVAL_SECONDS, INTERVAL_SECONDS, TimeUnit.SECONDS);
        ScheduledFuture<?> previous = running.put(recordId, renewal);
        if (previous != null) previous.cancel(false);
    }

    public void stop(String recordId) {
        ScheduledFuture<?> renewal = running.remove(recordId);
        if (renewal != null) renewal.cancel(false);
        try {
            redisTemplate.delete(key(recordId));
        } catch (Exception e) {
            // 지우지 못해도 TTL이 지나면 사라진다.
            log.warn("Heartbeat 삭제 실패 - recordId={}", recordId, e);
        }
    }

    public boolean isAlive(String recordId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key(recordId)));
    }
}
