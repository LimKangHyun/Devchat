package project.api.domain.push.app;

import static org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT;

import java.util.HashSet;

import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import project.api.domain.chat.chatmessage.event.ChatMessagePushEvent;
import project.api.domain.chat.chatroom.dao.ChatParticipantRepository;
import project.api.domain.chat.chatroom.dao.ChatRoomRepository;
import project.api.domain.chat.chatroom.entity.ChatRoom;
import project.api.domain.member.presence.PresenceService;

/**
 * 새 채팅 메시지를 앱 화면을 보고 있지 않은 참여자에게 휴대폰 알림으로 보낸다.
 * - 방 알림을 끈 사람, 보낸 사람, 멘션된 사람(멘션 알림이 따로 감)은 뺀다
 * - 같은 방 알림은 tag가 같아 쌓이지 않고 최신 것으로 바뀐다
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatMessagePushListener {

    private final PushSender pushSender;
    private final PushNotificationService pushNotificationService;
    private final PresenceService presenceService;
    private final ChatParticipantRepository participantRepository;
    private final ChatRoomRepository chatRoomRepository;

    @Async
    @TransactionalEventListener(phase = AFTER_COMMIT)
    public void onMessage(ChatMessagePushEvent event) {
        if (!pushSender.isEnabled()) return;
        try {
            Set<Long> targets = new HashSet<>(participantRepository.findMessagePushTargets(event.roomId(), event.senderId()));
            targets.removeAll(event.mentionedMemberIds());
            if (targets.isEmpty()) return;
            targets.removeAll(presenceService.foregroundAmong(targets));
            if (targets.isEmpty()) return;

            ChatRoom room = chatRoomRepository.findById(event.roomId()).orElse(null);
            if (room == null) return;
            PushMessage message = toPushMessage(room, event);
            for (Long memberId : targets) {
                pushNotificationService.sendToMember(memberId, message);
            }
        } catch (Exception e) {
            log.warn("[웹푸시] 채팅 알림 실패 room={}: {}", event.roomId(), e.getMessage());
        }
    }

    static PushMessage toPushMessage(ChatRoom room, ChatMessagePushEvent event) {
        String body = (event.senderNickname() != null ? event.senderNickname() + ": " : "") + event.preview();
        // 프론트 앱 안 알림과 같은 tag라 같은 방 알림은 하나로 합쳐진다
        return new PushMessage(room.getName(), body, "/chat/" + room.getInviteCode(), "room-" + room.getId());
    }
}
