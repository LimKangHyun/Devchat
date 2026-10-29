package project.api.global.websocket.interceptor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import project.api.auth.app.RevokedSessionStore;
import project.api.auth.dto.MemberDetails;

/** 끊긴 기기의 웹소켓 연결이 메시지를 보내거나 새로 구독하지 못하게 프레임을 버린다 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RevokedSessionChannelInterceptor implements ChannelInterceptor {

    private final RevokedSessionStore revokedSessionStore;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompCommand command = StompHeaderAccessor.wrap(message).getCommand();
        // 연결, 끊기, 구독 취소는 그대로 둔다
        if (command != StompCommand.SEND && command != StompCommand.SUBSCRIBE) {
            return message;
        }

        String sessionId = sessionId(message);
        if (sessionId != null && revokedSessionStore.isRevoked(sessionId)) {
            log.info("[웹소켓] 끊긴 세션의 {} 프레임을 버림 - sid={}", command, sessionId);
            return null;
        }
        return message;
    }

    // 옛 토큰으로 연결했으면 sid가 없다
    private String sessionId(Message<?> message) {
        if (StompHeaderAccessor.wrap(message).getUser() instanceof Authentication auth
                && auth.getPrincipal() instanceof MemberDetails member) {
            return member.getSessionId();
        }
        return null;
    }
}
