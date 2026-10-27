package project.api.domain.github.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import project.api.domain.github.app.GitMessageService;

class GitHubWebhookReceiverTest {

    private static final String SECRET = "test-secret";
    private static final byte[] BODY = "{\"action\":\"opened\"}".getBytes(StandardCharsets.UTF_8);

    private final GitHubWebhookVerifier verifier = new GitHubWebhookVerifier(SECRET);
    private GitMessageService gitMessageService;
    private RedisTemplate<String, String> redisTemplate;
    private ValueOperations<String, String> valueOps;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        gitMessageService = mock(GitMessageService.class);
        redisTemplate = mock(RedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        given(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).willReturn(true);
    }

    private GitHubWebhookReceiver receiver(boolean requireSignature) {
        return new GitHubWebhookReceiver(verifier, gitMessageService, redisTemplate, new ObjectMapper(), requireSignature);
    }

    private String validSignature() {
        return "sha256=" + verifier.hmacHex(BODY);
    }

    @Test
    @DisplayName("서명이 맞으면 이벤트를 처리한다")
    void valid_processed() {
        var outcome = receiver(true).receive(1L, "pull_request", "d-1", validSignature(), BODY);

        assertThat(outcome).isEqualTo(GitHubWebhookReceiver.Outcome.PROCESSED);
        verify(gitMessageService).handleEvent(eq(1L), eq("pull_request"), any());
    }

    @Test
    @DisplayName("서명이 틀린 요청(위조)은 설정과 상관없이 거절하고 처리하지 않는다")
    void invalid_rejected() {
        var outcome = receiver(false).receive(1L, "pull_request", "d-1", "sha256=" + "0".repeat(64), BODY);

        assertThat(outcome).isEqualTo(GitHubWebhookReceiver.Outcome.REJECTED);
        verify(gitMessageService, never()).handleEvent(anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("서명이 없는 요청은 전환 기간(require=false)에는 받고, 강제(require=true)하면 거절한다")
    void missing_dependsOnRequireSetting() {
        assertThat(receiver(false).receive(1L, "push", "d-1", null, BODY))
            .isEqualTo(GitHubWebhookReceiver.Outcome.PROCESSED);
        assertThat(receiver(true).receive(1L, "push", "d-2", null, BODY))
            .isEqualTo(GitHubWebhookReceiver.Outcome.REJECTED);
    }

    @Test
    @DisplayName("이미 처리한 전달(X-GitHub-Delivery)이 다시 오면 처리하지 않는다")
    void duplicateDelivery_ignored() {
        given(valueOps.setIfAbsent(eq("github:webhook:delivery:d-1"), anyString(), any(Duration.class))).willReturn(false);

        var outcome = receiver(true).receive(1L, "pull_request", "d-1", validSignature(), BODY);

        assertThat(outcome).isEqualTo(GitHubWebhookReceiver.Outcome.DUPLICATE);
        verify(gitMessageService, never()).handleEvent(anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("처리에 실패하면 전달 기록을 지워 GitHub의 재전송은 다시 처리되게 한다")
    void failure_unmarksDelivery() {
        willThrow(new IllegalStateException("db down")).given(gitMessageService).handleEvent(anyLong(), anyString(), any());

        assertThatThrownBy(() -> receiver(true).receive(1L, "pull_request", "d-1", validSignature(), BODY))
            .isInstanceOf(IllegalStateException.class);
        verify(redisTemplate).delete("github:webhook:delivery:d-1");
    }
}
