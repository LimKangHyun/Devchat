package project.ai.indexing;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import project.common.message.index.IndexingKeys;
import project.common.message.index.IndexingProgressMessage;

/**
 * 인덱싱 진행률을 Redis에 기록하고 API 서버에 알린다.
 * - Hash(repo:indexing:progress:{roomId}): 현재 상태. 채팅방에 나중에 들어온 사람이 읽는다.
 * - Pub/Sub(repo-index:progress): 변경 알림. API 서버가 받아 채팅방 WebSocket으로 전달한다.
 * 진행률은 화면 표시용이라, 기록에 실패해도 인덱싱은 멈추지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IndexingProgressReporter {

    /** 같은 단계 안에서 알림을 보내는 최소 간격. 배치가 빨리 끝나도 WebSocket을 몰아치지 않게 한다. */
    private static final long MIN_INTERVAL_MS = 500;
    /** 인덱싱이 비정상 종료돼 정리되지 못해도 진행률이 영원히 남지 않게 한다. */
    private static final Duration PROGRESS_TTL = Duration.ofHours(1);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private final Map<Long, Long> lastPublishedAt = new ConcurrentHashMap<>();
    private final Map<Long, String> lastPhase = new ConcurrentHashMap<>();

    /** 단계가 바뀌었거나, 마지막 개수이거나, 최소 간격이 지났으면 기록·알림한다. */
    public void report(Long roomId, String phase, int done, int total) {
        long now = System.currentTimeMillis();
        boolean phaseChanged = !phase.equals(lastPhase.put(roomId, phase));
        boolean finished = total > 0 && done >= total;
        Long last = lastPublishedAt.get(roomId);
        if (!phaseChanged && !finished && last != null && now - last < MIN_INTERVAL_MS) return;
        lastPublishedAt.put(roomId, now);

        try {
            String key = IndexingKeys.progressKey(roomId);
            redisTemplate.opsForHash().putAll(key, Map.of(
                "phase", phase, "done", String.valueOf(done), "total", String.valueOf(total)));
            redisTemplate.expire(key, PROGRESS_TTL);
            redisTemplate.convertAndSend(IndexingKeys.PROGRESS_CHANNEL,
                objectMapper.writeValueAsString(new IndexingProgressMessage(roomId, phase, done, total)));
        } catch (Exception e) {
            log.warn("[인덱싱] 진행률 기록 실패, 인덱싱은 계속 진행. roomId={}, phase={}", roomId, phase, e);
        }
    }

    /** 인덱싱이 끝나면(성공·실패·취소 모두) 진행률을 지운다. 최종 상태는 결과 스트림으로 따로 알린다. */
    public void clear(Long roomId) {
        lastPublishedAt.remove(roomId);
        lastPhase.remove(roomId);
        try {
            redisTemplate.delete(IndexingKeys.progressKey(roomId));
        } catch (Exception e) {
            log.warn("[인덱싱] 진행률 삭제 실패. roomId={}", roomId, e);
        }
    }
}
