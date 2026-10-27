package project.api.global.websocket.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import project.api.auth.dto.MemberDetails;
import project.api.domain.chat.chatroom.dao.ChatParticipantRepository;

/**
 * 채팅방 구독(/topic/chat/{roomId})은 참여 중인 방만 허용한다.
 *
 * 거절할 때 예외를 던지면 Spring이 ERROR 프레임을 보내고 웹소켓 연결 전체를 닫는다.
 * 클라이언트는 재연결 후 같은 구독(나간 방, 오래된 방 목록 캐시 등)을 다시 보내 끊김이 반복되므로,
 * 거절할 구독은 그 프레임만 버리고(null 반환) 연결과 다른 구독은 유지한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StompHandler implements ChannelInterceptor {

    private static final String CHAT_TOPIC_PREFIX = "/topic/chat/";

    private final ChatParticipantRepository chatParticipantRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {

        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

        if (!StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            return message;
        }

        String destination = accessor.getDestination();
        if (destination == null || !destination.startsWith(CHAT_TOPIC_PREFIX)) {
            return message;
        }

        Long roomId = extractRoomId(destination);
        if (roomId == null) {
            log.warn("[STOMP] 잘못된 채팅방 구독 주소 무시: {}", destination);
            return null;
        }

        if (!(accessor.getUser() instanceof Authentication authentication)
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof MemberDetails user)) {
            log.warn("[STOMP] 인증 정보 없는 채팅방 구독 무시: room={}", roomId);
            return null;
        }

        Long memberId = user.getId();
        if (!chatParticipantRepository.existsByParticipantIdAndChatRoomIdAndIsActiveTrue(memberId, roomId)) {
            log.info("[STOMP] 참여하지 않은 채팅방 구독 무시: member={}, room={}", memberId, roomId);
            return null;
        }

        accessor.getSessionAttributes().put("currentRoomId", roomId);
        accessor.getSessionAttributes().put("memberId", memberId);
        return message;
    }

    // /topic/chat/{roomId}[/...] 에서 roomId를 꺼낸다. 형식이 틀리면 null
    private Long extractRoomId(String destination) {
        String[] parts = destination.split("/");
        if (parts.length < 4) {
            return null;
        }
        try {
            return Long.parseLong(parts[3]);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
