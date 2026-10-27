package project.api.domain.github.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GitHubWebhookVerifierTest {

    // GitHub 공식 문서(Validating webhook deliveries)의 예시 값
    private static final String SECRET = "It's a Secret to Everybody";
    private static final byte[] BODY = "Hello, World!".getBytes(StandardCharsets.UTF_8);
    private static final String SIGNATURE = "sha256=757107ea0eb2509fc211221cce984b8a37570b6d7586c22c46f4379c8b043e17";

    private final GitHubWebhookVerifier verifier = new GitHubWebhookVerifier(SECRET);

    @Test
    @DisplayName("GitHub 문서의 예시 서명과 같은 값을 계산한다")
    void valid() {
        assertThat(verifier.verify(BODY, SIGNATURE)).isEqualTo(GitHubWebhookVerifier.Result.VALID);
    }

    @Test
    @DisplayName("본문이 한 글자라도 바뀌면 서명이 맞지 않는다")
    void tamperedBody() {
        byte[] tampered = "Hello, World?".getBytes(StandardCharsets.UTF_8);
        assertThat(verifier.verify(tampered, SIGNATURE)).isEqualTo(GitHubWebhookVerifier.Result.INVALID);
    }

    @Test
    @DisplayName("다른 secret으로 만든 서명이나 형식이 틀린 헤더는 거절한다")
    void wrongSecretOrFormat() {
        GitHubWebhookVerifier other = new GitHubWebhookVerifier("another-secret");
        assertThat(other.verify(BODY, SIGNATURE)).isEqualTo(GitHubWebhookVerifier.Result.INVALID);
        assertThat(verifier.verify(BODY, "sha1=abc")).isEqualTo(GitHubWebhookVerifier.Result.INVALID);
    }

    @Test
    @DisplayName("서명 헤더가 없으면 MISSING, 서버에 secret이 없으면 검증하지 않는다")
    void missingAndNotConfigured() {
        assertThat(verifier.verify(BODY, null)).isEqualTo(GitHubWebhookVerifier.Result.MISSING);
        assertThat(new GitHubWebhookVerifier("").verify(BODY, SIGNATURE))
            .isEqualTo(GitHubWebhookVerifier.Result.NOT_CONFIGURED);
    }
}
