package project.api.domain.community.listener;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import project.api.domain.notification.UserNotificationSender;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import project.api.domain.community.dto.event.ApplicantResultEvent;
import project.api.domain.community.dto.event.ApplyEvent;
import project.api.domain.member.entity.Member;
import project.api.domain.notification.app.NotificationService;
import project.api.domain.notification.dto.NotificationDto;
import project.api.domain.notification.entity.Notification;
import project.api.domain.notification.entity.NotificationType;

@Component
@RequiredArgsConstructor
public class ApplicantEventListener {

    private final UserNotificationSender notificationSender;
    private final NotificationService notificationService;

    @EventListener
    @Transactional
    public void handleApply(ApplyEvent event) {
        Member receiver = Member.builder().id(event.authorId()).build();
        Member sender = Member.builder().id(event.applicantId()).build();

        Notification saved = notificationService.saveNotification(
            Notification.ofStudyApply(receiver, sender, event.postId())
        );

        notificationSender.send(event.authorUsername(), NotificationDto.ofNotification(saved)); // 방장
    }

    @EventListener
    @Transactional
    public void handleApplicantResult(ApplicantResultEvent event) {
        Member receiver = Member.builder().id(event.applicantMemberId()).build();
        Member sender = Member.builder().id(event.authorId()).build();

        NotificationType type = event.approved()
            ? NotificationType.STUDY_APPROVED
            : NotificationType.STUDY_REJECTED;

        Notification saved = notificationService.saveNotification(
            Notification.ofStudyResult(receiver, sender, event.postId(), type)
        );

        notificationSender.send(event.applicantUsername(), NotificationDto.ofNotification(saved)); // 신청자
    }
}