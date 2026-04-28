package project.backend.global.websocket.interceptor;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import project.backend.auth.dto.MemberDetails;
import project.backend.domain.chat.chatmessage.app.policy.limiter.UserRateLimiter;
import project.backend.global.exception.errorcode.ChatMessageErrorCode;
import project.backend.global.exception.ex.ChatMessageException;

@Component
@RequiredArgsConstructor
public class RateLimitChannelInterceptor implements ChannelInterceptor {

    private final UserRateLimiter userRateLimiter;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

        if (StompCommand.SEND.equals(accessor.getCommand())) {
            Authentication authentication = (Authentication) accessor.getUser();
            if (authentication == null || !authentication.isAuthenticated()) {
                return message;
            }
            MemberDetails user = (MemberDetails) authentication.getPrincipal();
            if (!userRateLimiter.allow(user.getId())) {
                throw new ChatMessageException(ChatMessageErrorCode.TOO_MANY_REQUESTS);
            }
        }
        return message;
    }
}