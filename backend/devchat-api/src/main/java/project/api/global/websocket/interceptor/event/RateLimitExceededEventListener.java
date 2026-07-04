package project.api.global.websocket.interceptor.event;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class RateLimitExceededEventListener {

    private final SimpMessagingTemplate messagingTemplate;

    @EventListener
    public void handle(RateLimitExceededEvent event) {
        messagingTemplate.convertAndSendToUser(
                event.username(),
                "/queue/errors",
                Map.of(
                        "type", "TOO_MANY_REQUESTS",
                        "message", "메시지 전송 횟수를 초과했어요. 잠시 후 다시 시도해주세요."
                )
        );
    }
}