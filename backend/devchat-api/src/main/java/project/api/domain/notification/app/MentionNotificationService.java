package project.api.domain.notification.app;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.api.domain.chat.chatmessage.event.ChatMentionEvent;
import project.api.domain.chat.chatroom.dao.ChatParticipantRepository;
import project.api.domain.chat.chatroom.dao.ChatRoomRepository;
import project.api.domain.chat.chatroom.entity.ChatParticipant;
import project.api.domain.chat.chatroom.entity.ChatRoom;
import project.api.domain.member.entity.Member;
import project.api.domain.notification.dao.NotificationRepository;
import project.api.domain.notification.dto.NotificationDto;
import project.api.domain.notification.entity.Notification;

/**
 * @멘션 알림. 방의 활성 참여자 중 멘션된 사람에게만 보낸다(보낸 사람 본인, 봇 제외).
 * 방 알림을 꺼 둔 사람도 멘션은 받는다 (슬랙과 같은 동작).
 */
@Service
@RequiredArgsConstructor
public class MentionNotificationService {

    private final ChatParticipantRepository chatParticipantRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final NotificationRepository notificationRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${github.username}")
    private String githubBotUsername;

    @Value("${github.bot.username}")
    private String reviewBotUsername;

    /** @return 알림을 보낸 사람 수 */
    @Transactional
    public int notifyMentions(ChatMentionEvent event) {
        Set<Long> targets = new HashSet<>(event.mentionedMemberIds());
        targets.remove(event.senderId());
        if (targets.isEmpty()) {
            return 0;
        }

        List<ChatParticipant> participants = chatParticipantRepository.findByChatRoomId(event.roomId());
        Member sender = participants.stream()
            .map(ChatParticipant::getParticipant)
            .filter(m -> m.getId().equals(event.senderId()))
            .findFirst()
            .orElse(null);
        if (sender == null) {
            return 0; // 방 참여자가 아니면 멘션 알림을 보내지 않는다
        }
        String roomName = chatRoomRepository.findById(event.roomId()).map(ChatRoom::getName).orElse("채팅방");

        int sent = 0;
        for (ChatParticipant participant : participants) {
            Member receiver = participant.getParticipant();
            if (!targets.contains(receiver.getId()) || isBot(receiver)) {
                continue;
            }
            Notification saved = notificationRepository.save(Notification.ofMention(receiver, sender, event.roomId()));
            String content = sender.getNickname() + "님이 " + roomName + "에서 언급했습니다: " + event.preview();
            // NotificationEventListener가 커밋 뒤 받는 사람 세션(/user/queue/notifications)으로 보낸다
            eventPublisher.publishEvent(NotificationDto.ofNotification(saved).withContent(content));
            sent++;
        }
        return sent;
    }

    private boolean isBot(Member member) {
        return member.getUsername().equals(githubBotUsername) || member.getUsername().equals(reviewBotUsername);
    }
}
