package project.ai.budget;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 채팅방별 하루 LLM 토큰 예산.
 *
 * Redis 키 llm:budget:{roomId}:{yyyyMMdd}(한국 시간)에 그날 쓴 토큰을 누적한다. 외부에서 웹훅·커밋을 계속 보내도
 * 한 방이 쓸 수 있는 LLM 비용이 하루 한도를 넘지 않게 한다.
 * Redis를 읽지 못하면 리뷰를 막지 않는다(사용량 관리 때문에 본 기능이 멈추지 않게).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmBudgetService {

    public static final String PURPOSE_REVIEW = "review";
    public static final String PURPOSE_SUMMARY = "summary";

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;
    private static final Duration KEY_TTL = Duration.ofDays(2);

    private final StringRedisTemplate redisTemplate;
    private final MeterRegistry meterRegistry;

    @Value("${llm.budget.enabled:true}")
    private boolean enabled;

    /** 방 하나가 하루에 쓸 수 있는 토큰 (입력+출력) */
    @Value("${llm.budget.daily-tokens-per-room:500000}")
    private long dailyTokensPerRoom;

    /** 이 비율을 넘으면 LOW: 리뷰 맥락을 빼서 줄인다 */
    @Value("${llm.budget.low-ratio:0.8}")
    private double lowRatio;

    /** Grafana에 "No data" 대신 0이 보이도록 미리 등록한다 */
    @PostConstruct
    void registerMeters() {
        for (String purpose : new String[]{PURPOSE_REVIEW, PURPOSE_SUMMARY}) {
            meterRegistry.counter("llm.tokens", "purpose", purpose, "type", "prompt");
            meterRegistry.counter("llm.tokens", "purpose", purpose, "type", "output");
            meterRegistry.counter("llm.calls", "purpose", purpose);
            meterRegistry.counter("llm.budget.action", "purpose", purpose, "action", "reduced");
            meterRegistry.counter("llm.budget.action", "purpose", purpose, "action", "blocked");
        }
    }

    public BudgetLevel check(Long roomId) {
        if (!enabled || roomId == null) return BudgetLevel.OK;
        long used;
        try {
            String value = redisTemplate.opsForValue().get(key(roomId));
            used = value == null ? 0 : Long.parseLong(value);
        } catch (Exception e) {
            log.warn("LLM 사용량 조회 실패, 제한 없이 진행 - roomId={}", roomId, e);
            return BudgetLevel.OK;
        }
        return levelOf(used, dailyTokensPerRoom, lowRatio);
    }

    static BudgetLevel levelOf(long used, long limit, double lowRatio) {
        if (used >= limit) return BudgetLevel.EXCEEDED;
        if (used >= limit * lowRatio) return BudgetLevel.LOW;
        return BudgetLevel.OK;
    }

    /** 성공한 호출 한 번의 사용량을 지표와 방 예산에 더한다 */
    public void record(Long roomId, String purpose, LlmUsage usage) {
        meterRegistry.counter("llm.calls", "purpose", purpose).increment();
        meterRegistry.counter("llm.tokens", "purpose", purpose, "type", "prompt").increment(usage.promptTokens());
        meterRegistry.counter("llm.tokens", "purpose", purpose, "type", "output").increment(usage.outputTokens());

        if (!enabled || roomId == null || usage.totalTokens() == 0) return;
        try {
            String key = key(roomId);
            Long total = redisTemplate.opsForValue().increment(key, usage.totalTokens());
            if (total != null && total == usage.totalTokens()) {
                redisTemplate.expire(key, KEY_TTL); // 그날 첫 기록일 때만 만료를 건다
            }
        } catch (Exception e) {
            log.warn("LLM 사용량 기록 실패 - roomId={}, tokens={}", roomId, usage.totalTokens(), e);
        }
    }

    /** 예산 때문에 줄이거나(reduced) 건너뛴(blocked) 횟수 */
    public void recordAction(String purpose, BudgetLevel level) {
        String action = level == BudgetLevel.EXCEEDED ? "blocked" : "reduced";
        meterRegistry.counter("llm.budget.action", "purpose", purpose, "action", action).increment();
    }

    private String key(Long roomId) {
        return "llm:budget:" + roomId + ":" + LocalDate.now(ZONE).format(DAY);
    }
}
