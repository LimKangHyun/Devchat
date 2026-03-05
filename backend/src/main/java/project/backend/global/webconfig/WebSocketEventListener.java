package project.backend.global.webconfig;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;
import project.backend.auth.dto.MemberDetails;
import java.security.Principal;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final RedisTemplate<String, String> redisTemplate;

    @EventListener
    public void handleSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String destination = accessor.getDestination();

        if (destination == null || !destination.startsWith("/topic/chat/")) {
            return;
        }

        String roomId = destination.replace("/topic/chat/", "");
        String sessionId = accessor.getSessionId();
        Long memberId = getMemberId(accessor);

        if (memberId == null) {
            return;
        }

        // 온라인 등록
        redisTemplate.opsForSet().add("online:" + roomId, memberId.toString());
        // 세션 매핑 저장
        redisTemplate.opsForHash().put("session:" + sessionId, "roomId", roomId);
        redisTemplate.opsForHash().put("session:" + sessionId, "memberId", memberId.toString());
        redisTemplate.expire("session:" + sessionId, 24, TimeUnit.HOURS);

        log.info("온라인 등록 - roomId: {}, memberId: {}", roomId, memberId);
    }

    @EventListener
    public void handleUnsubscribe(SessionUnsubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        removeSession(accessor.getSessionId());
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        removeSession(event.getSessionId());
    }

    private void removeSession(String sessionId) {
        String roomId = (String) redisTemplate.opsForHash().get("session:" + sessionId, "roomId");
        String memberId = (String) redisTemplate.opsForHash()
            .get("session:" + sessionId, "memberId");

        if (roomId != null && memberId != null) {
            redisTemplate.opsForSet().remove("online:" + roomId, memberId);
            redisTemplate.delete("session:" + sessionId);
            log.info("온라인 해제 - roomId: {}, memberId: {}", roomId, memberId);
        }
    }

    private Long getMemberId(StompHeaderAccessor accessor) {
        Principal principal = accessor.getUser();
        if (principal instanceof Authentication auth) {
            MemberDetails memberDetails = (MemberDetails) auth.getPrincipal();
            return memberDetails.getId();
        }
        return null;
    }
}