package project.api.domain.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import project.api.domain.notification.dto.NotificationDto;
import project.api.domain.push.app.NotificationPushDispatcher;

/**
 * 실시간 알림을 받는 사람의 웹소켓 세션에만 보낸다 (/user/queue/notifications).
 * 예전 /topic/notifications/{username}은 누구나 남의 알림을 구독할 수 있었다.
 *
 * 받는 사람이 앱을 열어 두지 않았으면 휴대폰 알림(웹 푸시)으로도 보낸다.
 */
@Component
@RequiredArgsConstructor
public class UserNotificationSender {

    public static final String DESTINATION = "/queue/notifications";

    private final SimpMessagingTemplate messagingTemplate;
    private final NotificationPushDispatcher pushDispatcher;

    public void send(String receiverUsername, Object payload) {
        messagingTemplate.convertAndSendToUser(receiverUsername, DESTINATION, payload);
        if (payload instanceof NotificationDto notification) {
            pushDispatcher.dispatch(receiverUsername, notification);
        }
    }
}
