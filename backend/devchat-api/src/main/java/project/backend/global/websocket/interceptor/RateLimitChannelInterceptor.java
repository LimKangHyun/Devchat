package project.backend.global.websocket.interceptor;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import project.backend.auth.dto.MemberDetails;
import project.backend.global.ratelimit.UserRateLimiter;
import project.backend.global.websocket.interceptor.event.RateLimitExceededEvent;

@Component
@RequiredArgsConstructor
public class RateLimitChannelInterceptor implements ChannelInterceptor {

    private final CircuitBreakerRegistry registry;
    private final UserRateLimiter userRateLimiter;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

        if (StompCommand.SEND.equals(accessor.getCommand())) {
            Authentication authentication = (Authentication) accessor.getUser();
            if (authentication == null || !authentication.isAuthenticated()) {
                return message;
            }
            MemberDetails user = (MemberDetails) authentication.getPrincipal();

            if (!isAllowed(user.getId())) {
                eventPublisher.publishEvent(new RateLimitExceededEvent(user.getUsername()));
                return null;
            }
        }
        return message;
    }

    private boolean isAllowed(Long userId) {
        return isDegraded()
                ? userRateLimiter.allowStrict(userId)
                : userRateLimiter.allow(userId);
    }

    private boolean isDegraded() {
        return registry.circuitBreaker("redis").getState() == CircuitBreaker.State.OPEN;
    }
}