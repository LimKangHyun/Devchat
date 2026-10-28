package project.api.domain.chat.chatroom.event;

import static org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import project.api.domain.member.dao.MemberRepository;

/**
 * 내가 방을 만들거나 들어가거나 나가면, 내 모든 창·기기에 "방 목록이 바뀌었다"고 알린다.
 * 사이드바는 처음 불러온 방만 구독하므로, 다른 창에서 들어간 방의 메시지는 새로고침 전까지
 * 안읽음·알림에 잡히지 않았다. 받는 쪽은 /user/queue/rooms 를 받으면 방 목록을 다시 불러온다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoomMembershipNotifier {

    public static final String DESTINATION = "/queue/rooms";

    private final SimpMessagingTemplate messagingTemplate;
    private final MemberRepository memberRepository;

    @Async("chatRoomEventExecutor")
    @TransactionalEventListener(phase = AFTER_COMMIT, fallbackExecution = true)
    public void onCreated(RoomCreatedByMemberEvent event) {
        notifyMember(event.memberId(), "CREATED", event.roomId());
    }

    @Async("chatRoomEventExecutor")
    @TransactionalEventListener(phase = AFTER_COMMIT, fallbackExecution = true)
    public void onJoined(JoinChatRoomEvent event) {
        notifyMember(event.memberId(), "JOINED", event.roomId());
    }

    @Async("chatRoomEventExecutor")
    @TransactionalEventListener(phase = AFTER_COMMIT, fallbackExecution = true)
    public void onLeft(LeaveChatRoomEvent event) {
        notifyMember(event.memberId(), "LEFT", event.roomId());
    }

    private void notifyMember(Long memberId, String type, Long roomId) {
        try {
            memberRepository.findById(memberId).ifPresent(member ->
                messagingTemplate.convertAndSendToUser(member.getUsername(), DESTINATION,
                    Map.of("type", type, "roomId", roomId)));
        } catch (Exception e) {
            log.warn("[방 목록 갱신 알림] 실패 member={}, room={}: {}", memberId, roomId, e.getMessage());
        }
    }
}
