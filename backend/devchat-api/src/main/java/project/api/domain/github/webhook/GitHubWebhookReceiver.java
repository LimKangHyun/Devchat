package project.api.domain.github.webhook;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import project.api.domain.github.app.GitMessageService;

/**
 * GitHub webhook 수신 처리: 서명 검증 → 중복 전달 차단 → 이벤트 처리.
 *
 * 서명 헤더가 없는 요청은 require-signature 설정에 따라 다룬다. secret 없이 등록된 예전 webhook이 남아 있는 동안에는
 * 받되 경고를 남기고(false), 모든 webhook을 갱신한 뒤 true로 바꿔 거절한다. 서명이 틀린 요청은 설정과 상관없이 거절한다.
 */
@Slf4j
@Component
public class GitHubWebhookReceiver {

    /** GitHub는 실패한 전달을 다시 보내거나 같은 전달을 수동으로 재전송할 수 있다. 하루 안의 중복을 막는다. */
    static final Duration DELIVERY_TTL = Duration.ofDays(1);
    private static final String DELIVERY_KEY_PREFIX = "github:webhook:delivery:";

    public enum Outcome {
        PROCESSED,
        /** 이미 처리한 전달이라 무시함 */
        DUPLICATE,
        /** 서명이 틀리거나(위조) 서명이 필요한데 없음 */
        REJECTED
    }

    private final GitHubWebhookVerifier verifier;
    private final GitMessageService gitMessageService;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final boolean requireSignature;

    public GitHubWebhookReceiver(GitHubWebhookVerifier verifier, GitMessageService gitMessageService,
                                 RedisTemplate<String, String> redisTemplate, ObjectMapper objectMapper,
                                 @Value("${github.webhook.require-signature:false}") boolean requireSignature) {
        this.verifier = verifier;
        this.gitMessageService = gitMessageService;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.requireSignature = requireSignature;
    }

    public Outcome receive(Long roomId, String eventType, String deliveryId, String signature, byte[] body) {
        GitHubWebhookVerifier.Result verification = verifier.verify(body, signature);
        switch (verification) {
            case INVALID -> {
                log.warn("[WEBHOOK] 서명 불일치, 거절 - roomId={}, event={}, delivery={}", roomId, eventType, deliveryId);
                return Outcome.REJECTED;
            }
            case MISSING -> {
                if (requireSignature) {
                    log.warn("[WEBHOOK] 서명 없음, 거절 - roomId={}, event={}, delivery={}", roomId, eventType, deliveryId);
                    return Outcome.REJECTED;
                }
                log.warn("[WEBHOOK] 서명 없음(secret 없이 등록된 webhook), 임시로 허용 - roomId={}, event={}. "
                    + "채팅방에서 GitHub 연동을 갱신해 주세요.", roomId, eventType);
            }
            case VALID, NOT_CONFIGURED -> { }
        }

        if (!markDelivery(deliveryId)) {
            log.info("[WEBHOOK] 이미 처리한 전달, 무시 - roomId={}, event={}, delivery={}", roomId, eventType, deliveryId);
            return Outcome.DUPLICATE;
        }

        try {
            Map<String, Object> payload = objectMapper.readValue(body, new TypeReference<>() {});
            gitMessageService.handleEvent(roomId, eventType, payload);
            return Outcome.PROCESSED;
        } catch (RuntimeException | java.io.IOException e) {
            // 처리에 실패하면 기록을 지워, GitHub가 다시 보낸 전달은 처리되게 한다.
            unmarkDelivery(deliveryId);
            if (e instanceof RuntimeException re) throw re;
            throw new IllegalArgumentException("webhook 본문을 읽을 수 없음", e);
        }
    }

    /** 처음 받은 전달이면 true. Redis 장애 시에는 처리를 막지 않도록 true(중복 허용). */
    private boolean markDelivery(String deliveryId) {
        if (deliveryId == null || deliveryId.isBlank()) return true;
        try {
            return Boolean.TRUE.equals(
                redisTemplate.opsForValue().setIfAbsent(DELIVERY_KEY_PREFIX + deliveryId, "1", DELIVERY_TTL));
        } catch (Exception e) {
            log.warn("[WEBHOOK] 중복 확인 실패(Redis), 처리 계속 - delivery={}", deliveryId, e);
            return true;
        }
    }

    private void unmarkDelivery(String deliveryId) {
        if (deliveryId == null || deliveryId.isBlank()) return;
        try {
            redisTemplate.delete(DELIVERY_KEY_PREFIX + deliveryId);
        } catch (Exception e) {
            log.warn("[WEBHOOK] 중복 기록 삭제 실패 - delivery={}", deliveryId, e);
        }
    }
}
