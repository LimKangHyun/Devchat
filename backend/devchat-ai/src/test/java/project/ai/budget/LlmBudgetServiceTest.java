package project.ai.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

class LlmBudgetServiceTest {

    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    ValueOperations<String, String> ops = mock(ValueOperations.class);
    SimpleMeterRegistry meters = new SimpleMeterRegistry();
    LlmBudgetService service = new LlmBudgetService(redis, meters);

    @BeforeEach
    void setUp() {
        given(redis.opsForValue()).willReturn(ops);
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "dailyTokensPerRoom", 1000L);
        ReflectionTestUtils.setField(service, "lowRatio", 0.8);
    }

    @Test
    void 사용량에_따라_단계가_바뀐다() {
        assertThat(LlmBudgetService.levelOf(799, 1000, 0.8)).isEqualTo(BudgetLevel.OK);
        assertThat(LlmBudgetService.levelOf(800, 1000, 0.8)).isEqualTo(BudgetLevel.LOW);
        assertThat(LlmBudgetService.levelOf(1000, 1000, 0.8)).isEqualTo(BudgetLevel.EXCEEDED);
    }

    @Test
    void 레디스에서_오늘_사용량을_읽는다() {
        given(ops.get(anyString())).willReturn("1200");
        assertThat(service.check(1L)).isEqualTo(BudgetLevel.EXCEEDED);
    }

    @Test
    void 레디스_오류면_막지_않는다() {
        given(ops.get(anyString())).willThrow(new RuntimeException("down"));
        assertThat(service.check(1L)).isEqualTo(BudgetLevel.OK);
    }

    @Test
    void 사용량을_누적하고_그날_첫_기록에만_만료를_건다() {
        given(ops.increment(anyString(), eq(300L))).willReturn(300L, 600L);

        service.record(1L, LlmBudgetService.PURPOSE_REVIEW, new LlmUsage(200, 100));
        verify(redis).expire(anyString(), eq(Duration.ofDays(2)));

        service.record(1L, LlmBudgetService.PURPOSE_REVIEW, new LlmUsage(200, 100));
        verify(redis, org.mockito.Mockito.times(1)).expire(anyString(), eq(Duration.ofDays(2)));

        assertThat(meters.counter("llm.tokens", "purpose", "review", "type", "prompt").count()).isEqualTo(400);
        assertThat(meters.counter("llm.tokens", "purpose", "review", "type", "output").count()).isEqualTo(200);
        assertThat(meters.counter("llm.calls", "purpose", "review").count()).isEqualTo(2);
    }

    @Test
    void 방_정보가_없으면_지표만_남긴다() {
        service.record(null, LlmBudgetService.PURPOSE_SUMMARY, new LlmUsage(10, 5));
        verify(ops, never()).increment(anyString(), org.mockito.ArgumentMatchers.anyLong());
        assertThat(meters.counter("llm.calls", "purpose", "summary").count()).isEqualTo(1);
    }
}
