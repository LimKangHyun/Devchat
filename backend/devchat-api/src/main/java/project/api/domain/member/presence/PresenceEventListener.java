package project.api.domain.member.presence;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import project.api.auth.dto.MemberDetails;
import project.api.domain.member.friend.dao.FriendsListRepository;

import java.util.List;

/**
 * 웹소켓 접속/끊김으로 온라인 상태를 기록하고, 상태가 바뀌면 나를 친구로 둔 사람들에게 알린다.
 * 받는 쪽은 /user/queue/presence 를 구독한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PresenceEventListener {

    public static final String PRESENCE_DESTINATION = "/queue/presence";

    private final PresenceService presenceService;
    private final FriendsListRepository friendsListRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public record PresenceUpdate(String username, String status) {}

    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        if (!(event.getUser() instanceof Authentication auth)
                || !(auth.getPrincipal() instanceof MemberDetails member)) {
            return;
        }
        String sessionId = (String) event.getMessage().getHeaders().get("simpSessionId");
        if (sessionId == null) return;

        try {
            if (presenceService.connect(member.getId(), member.getUsername(), sessionId)) {
                notifyFriends(member.getId(), member.getUsername(), PresenceService.ONLINE);
            }
        } catch (Exception e) {
            log.warn("[접속상태] 접속 기록 실패 - member={}: {}", member.getId(), e.getMessage());
        }
    }

    @EventListener
    public void onDisconnected(SessionDisconnectEvent event) {
        try {
            presenceService.disconnect(event.getSessionId())
                    .ifPresent(m -> notifyFriends(m.id(), m.username(), PresenceService.OFFLINE));
        } catch (Exception e) {
            log.warn("[접속상태] 끊김 기록 실패 - session={}: {}", event.getSessionId(), e.getMessage());
        }
    }

    private void notifyFriends(Long memberId, String username, String status) {
        List<String> receivers = friendsListRepository.findOwnerUsernamesByFriendId(memberId);
        PresenceUpdate update = new PresenceUpdate(username, status);
        for (String receiver : receivers) {
            messagingTemplate.convertAndSendToUser(receiver, PRESENCE_DESTINATION, update);
        }
    }
}
