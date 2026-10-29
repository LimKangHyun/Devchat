package project.api.auth.app;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SessionRevokedNotifierTest {

    @Mock SimpMessagingTemplate messagingTemplate;
    @InjectMocks SessionRevokedNotifier notifier;

    @Test
    void notifyRevoked_sendsToUserSessionQueue() {
        notifier.notifyRevoked(new SessionRevokedEvent("user1", List.of("s-2", "s-3")));

        verify(messagingTemplate).convertAndSendToUser("user1", "/queue/session",
                Map.of("type", "SESSION_REVOKED", "sessionIds", List.of("s-2", "s-3")));
    }
}
