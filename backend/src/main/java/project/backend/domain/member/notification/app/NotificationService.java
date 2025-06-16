package project.backend.domain.member.notification.app;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.backend.auth.dto.MemberDetails;
import project.backend.domain.member.app.MemberService;
import project.backend.domain.member.entity.Member;
import project.backend.domain.member.friend.dto.event.FriendEvent;
import project.backend.domain.member.notification.dao.NotificationRepository;
import project.backend.domain.member.notification.dto.AlertTemplate;
import project.backend.domain.member.notification.entity.Notification;
import project.backend.domain.member.notification.entity.NotificationType;
import project.backend.global.exception.errorcode.NotificationErrorCode;
import project.backend.global.exception.ex.NotificationException;

@Service
@RequiredArgsConstructor
public class NotificationService {

	private final NotificationRepository notificationRepository;
	private final MemberService memberService;

	@Transactional(readOnly = true)
	public Page<FriendEvent> getNotifications(Authentication auth, Pageable pageable) {
		MemberDetails memberDetails = memberService.checkAuthentication(auth);

		Member receiver = MemberDetails.of(memberDetails);
		Page<Notification> notReadNotification = notificationRepository.getNotificationsAndReadNot(
			receiver.getId(), pageable);

		return notReadNotification.map(FriendEvent::ofNotification);
	}

	public void saveNotification(Notification notification) {
		notificationRepository.save(notification);
	}

	public Notification getNotificationByType(Member receiver, Member sender,
		NotificationType type) {
		return notificationRepository.getNotificationByType(receiver, sender, type)
			.orElseThrow(
				() -> new NotificationException(NotificationErrorCode.NOT_FOUND_NOTIFICATION));
	}

	public void deleteNotification(Notification notification) {
		notificationRepository.delete(notification);
	}
}
