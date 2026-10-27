package project.api.domain.github.webhook;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * GitHub webhook 요청이 실제로 GitHub에서 왔는지 확인한다.
 * GitHub는 webhook secret으로 요청 본문 원문의 HMAC-SHA256을 계산해 X-Hub-Signature-256 헤더("sha256=<hex>")로 보낸다.
 * 같은 secret으로 다시 계산해 비교하면, secret을 모르는 쪽이 보낸 위조 요청을 걸러낼 수 있다.
 */
@Slf4j
@Component
public class GitHubWebhookVerifier {

    private static final String PREFIX = "sha256=";

    public enum Result {
        /** 서명이 맞음 */
        VALID,
        /** 서명 헤더가 없음. secret 없이 등록된 예전 webhook이거나 위조 요청 */
        MISSING,
        /** 서명이 틀림. 위조됐거나 본문이 바뀐 요청 */
        INVALID,
        /** 서버에 secret이 설정되지 않아 검증하지 않음(로컬 개발 등) */
        NOT_CONFIGURED
    }

    private final byte[] secret;

    public GitHubWebhookVerifier(@Value("${github.webhook.secret:}") String secret) {
        this.secret = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if (this.secret.length == 0) {
            log.warn("github.webhook.secret이 비어 있어 webhook 서명을 검증하지 않습니다. 운영에서는 반드시 설정하세요.");
        }
    }

    public boolean isConfigured() {
        return secret.length > 0;
    }

    /**
     * @param body            요청 본문 원문. JSON으로 파싱했다가 다시 직렬화하면 바이트가 달라져 서명이 맞지 않으므로 원문을 써야 한다
     * @param signatureHeader X-Hub-Signature-256 헤더 값
     */
    public Result verify(byte[] body, String signatureHeader) {
        if (!isConfigured()) return Result.NOT_CONFIGURED;
        if (signatureHeader == null || signatureHeader.isBlank()) return Result.MISSING;
        if (!signatureHeader.startsWith(PREFIX)) return Result.INVALID;

        byte[] expected = (PREFIX + hmacHex(body)).getBytes(StandardCharsets.UTF_8);
        byte[] actual = signatureHeader.getBytes(StandardCharsets.UTF_8);
        // 상수 시간 비교: 앞에서부터 몇 글자가 맞는지에 따라 응답 시간이 달라지면, 서명을 한 글자씩 추측할 수 있다.
        return MessageDigest.isEqual(expected, actual) ? Result.VALID : Result.INVALID;
    }

    String hmacHex(byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] digest = mac.doFinal(body);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("HMAC 계산 실패", e);
        }
    }
}
