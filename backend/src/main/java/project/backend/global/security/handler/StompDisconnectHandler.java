package project.backend.global.security.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import project.backend.domain.chat.chatroom.app.ChatRoomReadService;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class StompDisconnectHandler {

    private final ChatRoomReadService chatRoomReadService;

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();

        if (sessionAttributes == null) return;

        Long roomId = (Long) sessionAttributes.get("currentRoomId");
        Long memberId = (Long) sessionAttributes.get("memberId");

        if (roomId == null || memberId == null) return;

        try {
            chatRoomReadService.updateLastReadSequence(roomId, memberId);
            log.info("비정상 종료 안읽음 동기화 완료 - memberId: {}, roomId: {}", memberId, roomId);
        } catch (Exception e) {
            log.warn("비정상 종료 안읽음 동기화 실패 - memberId: {}, roomId: {}", memberId, roomId);
        }
    }
}