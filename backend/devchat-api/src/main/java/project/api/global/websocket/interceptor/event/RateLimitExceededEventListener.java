package project.api.global.websocket.interceptor.event;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

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
        body.put("message", "메시지 전송 횟수를 초과했어요. 잠시 후 다시 시도해주세요.");
        // 어느 메시지가 막혔는지 알려줘야 화면에서 그 메시지를 "전송 실패"로 표시할 수 있다
        if (event.clientMessageId() != null) body.put("clientMessageId", event.clientMessageId());
        messagingTemplate.convertAndSendToUser(event.username(), "/queue/errors", body);
    }
}