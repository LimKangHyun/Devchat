package project.api.domain.feedback.app;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import project.api.domain.member.app.MemberService;
import project.api.domain.member.entity.Member;
import project.api.domain.notification.UserNotificationSender;
import project.api.domain.notification.app.NotificationService;
import project.api.domain.notification.dto.NotificationDto;
import project.api.domain.notification.entity.Notification;
import project.api.domain.notification.entity.NotificationType;

/**
 * 피드백 소식을 보낸 사람에게 앱 알림(+ 앱을 안 보고 있으면 휴대폰 알림)으로 보낸다.
 * 보낸 사람은 GitHub 봇 계정으로 표시한다 (알림에는 보낸 사람이 꼭 있어야 한다).
 */
@Slf4j
@Component
public class FeedbackNotifier {

    private final NotificationService notificationService;
    private final UserNotificationSender notificationSender;
    private final MemberService memberService;
    private final TransactionTemplate saveTx;
    private final String botUsername;

    public FeedbackNotifier(NotificationService notificationService,
                            UserNotificationSender notificationSender,
                            MemberService memberService,
                            PlatformTransactionManager transactionManager,
                            @Value("${github.username}") String botUsername) {
        this.notificationService = notificationService;
        this.notificationSender = notificationSender;
        this.memberService = memberService;
        this.saveTx = new TransactionTemplate(transactionManager);
        this.botUsername = botUsername;
    }

    public void notify(Member receiver, NotificationType type, int issueNumber, String message, String link) {
        try {
            NotificationDto dto = saveTx.execute(status -> NotificationDto.ofNotification(
                    notificationService.saveNotification(Notification.ofFeedback(
                            receiver, memberService.getMemberByUsername(botUsername),
                            type, issueNumber, message, link))));
            // 저장이 커밋된 뒤에만 보낸다
            if (dto != null) notificationSender.send(dto.receiverUsername(), dto);
        } catch (Exception e) {
            log.warn("[피드백 알림] 전달 실패 type={} issue=#{}: {}", type, issueNumber, e.getMessage(), e);
        }
    }
}
