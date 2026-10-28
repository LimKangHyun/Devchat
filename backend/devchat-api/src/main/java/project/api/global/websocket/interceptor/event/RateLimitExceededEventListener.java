package project.api.global.websocket.interceptor.event;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import project.api.global.ratelimit.UserRateLimiter;

import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class RateLimitExceededEventListener {

    private final SimpMessagingTemplate messagingTemplate;

    @EventListener
    public void handle(RateLimitExceededEvent event) {
        Map<String, Object> body = new HashMap<>();
        body.put("type", "TOO_MANY_REQUESTS");
        body.put("message", "메시지를 너무 빠르게 보내고 있어요. 잠시 후 다시 보내 주세요.");
        // 화면은 이 시간 동안 전송을 막는다 (막힌 메시지는 다시 보내기 없이 버린다)
        body.put("retryAfterSeconds", UserRateLimiter.COOLDOWN_SECONDS);
        // 어느 메시지가 막혔는지 알려줘야 화면에서 그 메시지를 정리할 수 있다
        if (event.clientMessageId() != null) body.put("clientMessageId", event.clientMessageId());
        messagingTemplate.convertAndSendToUser(event.username(), "/queue/errors", body);
    }
}