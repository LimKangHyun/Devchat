package project.api.auth.app;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 세션이 끊기면 커밋 뒤 그 사용자의 웹소켓으로 알린다. 화면은 sessionIds에 자기 sid가 있으면 로그아웃한다 */
@Component
@RequiredArgsConstructor
public class SessionRevokedNotifier {

    public static final String DESTINATION = "/queue/session";

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void notifyRevoked(SessionRevokedEvent event) {
        messagingTemplate.convertAndSendToUser(event.username(), DESTINATION,
                Map.of("type", "SESSION_REVOKED", "sessionIds", event.sessionIds()));
    }
}
