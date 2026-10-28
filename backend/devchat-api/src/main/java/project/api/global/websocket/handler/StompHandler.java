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

import java.util.Map;

/**
 * 채팅방 구독(/topic/chat/{roomId})은 참여 중인 방만 허용한다.
 *
 * 거절할 때 예외를 던지면 Spring이 ERROR 프레임을 보내고 웹소켓 연결 전체를 닫는다.
 * 클라이언트는 재연결 후 같은 구독(나간 방, 오래된 방 목록 캐시 등)을 다시 보내 끊김이 반복되므로,
 * 거절할 구독은 그 프레임만 버리고(null 반환) 연결과 다른 구독은 유지한다.
 *
 * 끊길 때 읽음 처리할 "현재 방"은 사용자가 실제로 보고 있는 방의 구독(active-room 헤더)만 기록한다.
 * 사이드바는 안읽음 수를 세려고 모든 방을 구독하므로, 구독마다 기록하면 엉뚱한 방이 읽음 처리된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StompHandler implements ChannelInterceptor {

    public static final String ACTIVE_ROOM_HEADER = "active-room";
    static final String CURRENT_ROOM_ID = "currentRoomId";
    static final String ACTIVE_ROOM_SUB_ID = "activeRoomSubscriptionId";
    static final String MEMBER_ID = "memberId";

    private static final String CHAT_TOPIC_PREFIX = "/topic/chat/";

    private final ChatParticipantRepository chatParticipantRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {

        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

        if (StompCommand.UNSUBSCRIBE.equals(accessor.getCommand())) {
            clearCurrentRoomIfActive(accessor);
            return message;
        }
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

        Map<String, Object> attrs = accessor.getSessionAttributes();
        if (attrs != null) {
            attrs.put(MEMBER_ID, memberId);
            if ("true".equals(accessor.getFirstNativeHeader(ACTIVE_ROOM_HEADER))) {
                attrs.put(CURRENT_ROOM_ID, roomId);
                attrs.put(ACTIVE_ROOM_SUB_ID, accessor.getSubscriptionId());
            }
        }
        return message;
    }

    // 보고 있던 방의 구독을 해제하면(방에서 나감) 현재 방 기록을 지운다
    private void clearCurrentRoomIfActive(StompHeaderAccessor accessor) {
        Map<String, Object> attrs = accessor.getSessionAttributes();
        String subscriptionId = accessor.getSubscriptionId();
        if (attrs == null || subscriptionId == null) return;
        if (subscriptionId.equals(attrs.get(ACTIVE_ROOM_SUB_ID))) {
            attrs.remove(CURRENT_ROOM_ID);
            attrs.remove(ACTIVE_ROOM_SUB_ID);
        }
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
