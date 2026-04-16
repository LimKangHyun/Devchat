package project.backend.domain.chat.chatmessage.app.policy;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import project.backend.domain.chat.chatmessage.app.policy.limiter.UserRateLimiter;

@Component
@RequiredArgsConstructor
public class NormalMessageSendPolicy implements MessageSendPolicy {

    private final UserRateLimiter userRateLimiter;

    @Override
    public boolean canSend(Long userId) {
        return userRateLimiter.allow(userId);
    }
}