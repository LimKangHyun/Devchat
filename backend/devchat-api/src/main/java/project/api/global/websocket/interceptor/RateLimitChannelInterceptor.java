package project.api.global.websocket.interceptor;

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
import project.api.domain.chat.typing.TypingController;
import project.api.domain.member.presence.PresenceController;
import project.api.global.exception.ex.stomp.StompExceptionHandler;
import project.api.auth.dto.MemberDetails;
import project.api.global.ratelimit.UserRateLimiter;
import project.api.global.websocket.interceptor.event.RateLimitExceededEvent;

@Component
@RequiredArgsConstructor
public class RateLimitChannelInterceptor implements ChannelInterceptor {

    private final CircuitBreakerRegistry registry;
    private final UserRateLimiter userRateLimiter;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

        // 화면 보임 신호는 브라우저가 30초마다 자동으로 보내므로 메시지 전송 횟수에 넣지 않는다
        boolean presenceSignal = ("/chat" + PresenceController.VISIBILITY_DESTINATION).equals(accessor.getDestination());
        // 입력 중 신호도 타이핑하는 동안 자동으로 오가므로 넣지 않는다 (TypingService가 따로 횟수를 제한)
        String destination = accessor.getDestination();
        boolean typingSignal = destination != null && destination.startsWith("/chat" + TypingController.DESTINATION_PREFIX);
        if (StompCommand.SEND.equals(accessor.getCommand()) && !presenceSignal && !typingSignal) {
            Authentication authentication = (Authentication) accessor.getUser();
            if (authentication == null || !authentication.isAuthenticated()) {
                return message;
            }
            MemberDetails user = (MemberDetails) authentication.getPrincipal();

            if (!isAllowed(user.getId())) {
                eventPublisher.publishEvent(new RateLimitExceededEvent(user.getUsername(),
                        accessor.getFirstNativeHeader(StompExceptionHandler.CLIENT_MESSAGE_ID_HEADER)));
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