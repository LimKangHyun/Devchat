package project.api.domain.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * 실시간 알림을 받는 사람의 웹소켓 세션에만 보낸다 (/user/queue/notifications).
 * 예전 /topic/notifications/{username}은 누구나 남의 알림을 구독할 수 있었다.
 */
@Component
@RequiredArgsConstructor
public class UserNotificationSender {

    public static final String DESTINATION = "/queue/notifications";

    private final SimpMessagingTemplate messagingTemplate;

    public void send(String receiverUsername, Object payload) {
        messagingTemplate.convertAndSendToUser(receiverUsername, DESTINATION, payload);
    }
}
