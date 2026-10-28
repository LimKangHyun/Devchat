package project.api.domain.push.app;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import project.api.domain.chat.chatroom.dao.ChatRoomRepository;
import project.api.domain.chat.chatroom.entity.ChatRoom;
import project.api.domain.member.dao.MemberRepository;
import project.api.domain.member.entity.Member;
import project.api.domain.member.presence.PresenceService;
import project.api.domain.notification.dto.NotificationDto;

/**
 * 앱 안 알림을 휴대폰 알림으로도 보낸다.
 * 받는 사람이 지금 앱 화면을 보고 있으면 앱 안 알림으로 충분하므로 보내지 않는다.
 * (연결만 돼 있고 화면이 꺼졌거나 백그라운드면 보낸다)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationPushDispatcher {

    private final PushNotificationService pushNotificationService;
    private final PushSender pushSender;
    private final PresenceService presenceService;
    private final MemberRepository memberRepository;
    private final ChatRoomRepository chatRoomRepository;

    @Async
    public void dispatch(String receiverUsername, NotificationDto notification) {
        if (!pushSender.isEnabled() || receiverUsername == null) return;
        try {
            Member receiver = memberRepository.findByUsername(receiverUsername).orElse(null);
            if (receiver == null) return;
            // 지금 앱 화면을 보고 있으면 앱 안 알림으로 충분하다
            if (!presenceService.foregroundAmong(List.of(receiver.getId())).isEmpty()) return;

            pushNotificationService.sendToMember(receiver.getId(), toPushMessage(notification));
        } catch (Exception e) {
            log.warn("[웹푸시] 알림 전달 실패 receiver={}: {}", receiverUsername, e.getMessage());
        }
    }

    PushMessage toPushMessage(NotificationDto n) {
        String sender = n.senderNickname() != null ? n.senderNickname() : "누군가";
        String title = switch (n.type()) {
            case MENTION -> sender + "님이 회원님을 언급했어요";
            case NEW_DM -> sender + "님의 메시지";
            case FRIEND_REQUESTED, FRIEND_ACCEPTED, FRIEND_REJECTED, WE_ARE_FRIEND_NOW -> "친구";
            case STUDY_APPLY, STUDY_APPROVED, STUDY_REJECTED -> "스터디";
            case CODE_REVIEW -> "코드 리뷰";
        };
        String tag = n.type().name() + "-" + (n.referenceId() != null ? n.referenceId() : "");
        return new PushMessage(title, n.content(), urlFor(n), tag);
    }

    // 알림을 누르면 열 화면. 프론트 헤더의 알림 클릭 이동과 같은 규칙
    private String urlFor(NotificationDto n) {
        Long id = n.referenceId();
        if (id == null) return "/";
        return switch (n.type()) {
            case MENTION -> chatRoomRepository.findById(id)
                .map(ChatRoom::getInviteCode)
                .map(code -> "/chat/" + code)
                .orElse("/");
            case CODE_REVIEW -> "/code-review/" + id;
            case STUDY_APPLY -> "/community/" + id + "/applicants";
            case STUDY_APPROVED, STUDY_REJECTED -> "/community/" + id;
            default -> "/";
        };
    }
}
