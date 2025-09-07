package project.backend.domain.notification.app;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.backend.auth.dto.MemberDetails;
import project.backend.domain.member.app.MemberService;
import project.backend.domain.member.entity.Member;
import project.backend.domain.notification.dto.NotificationDto;
import project.backend.domain.notification.dao.NotificationRepository;
import project.backend.domain.notification.entity.Notification;
import project.backend.domain.notification.entity.NotificationType;
import project.backend.global.exception.errorcode.NotificationErrorCode;
import project.backend.global.exception.ex.NotificationException;

@Service
@RequiredArgsConstructor
public class NotificationService {

	private final NotificationRepository notificationRepository;
	private final MemberService memberService;

	@Transactional(readOnly = true)
	public Page<NotificationDto> getNotifications(Authentication auth, Pageable pageable) {
		MemberDetails memberDetails = memberService.checkAuthentication(auth);

		Member receiver = Member.of(memberDetails);
		Page<Notification> notReadNotification = notificationRepository.getNotifications(
			receiver.getId(), pageable);

		return notReadNotification.map(NotificationDto::ofNotification);
	}

	@Transactional(readOnly = true)
	public Page<NotificationDto> getNotReadNotification(Authentication auth,
		Pageable pageable) {
		MemberDetails memberDetails = memberService.checkAuthentication(auth);

		Member receiver = Member.of(memberDetails);
		Page<Notification> notReadNotification = notificationRepository.getNotReadNotification(
			receiver.getId(), pageable);

		return notReadNotification.map(NotificationDto::ofNotification);
	}

	public Notification saveNotification(Notification notification) {
		return notificationRepository.save(notification);
	}

	public Notification getNotificationByType(Member receiver, Member sender,
		NotificationType type) {
		return notificationRepository.getNotificationByType(receiver, sender, type)
			.orElseThrow(
				() -> new NotificationException(NotificationErrorCode.NOT_FOUND_NOTIFICATION));
	}

	private Notification getNotificationById(Long notificationId) {
		return notificationRepository.findById(notificationId)
			.orElseThrow(
				() -> new NotificationException(NotificationErrorCode.NOT_FOUND_NOTIFICATION));
	}

	@Transactional
	public void readNotification(Long notificationId, Authentication auth) {
		memberService.checkAuthentication(auth);

		Notification notification = getNotificationById(notificationId);
		notification.markAsRead();
	}
}
