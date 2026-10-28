package project.api.domain.notification.app;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.api.auth.dto.MemberDetails;
import project.api.domain.member.app.MemberService;
import project.api.domain.member.entity.Member;
import project.api.domain.notification.dto.NotificationDto;
import project.api.domain.notification.dao.NotificationRepository;
import project.api.domain.notification.entity.Notification;
import project.api.domain.notification.entity.NotificationType;
import project.api.global.exception.errorcode.NotificationErrorCode;
import project.api.global.exception.ex.NotificationException;

@Service
@RequiredArgsConstructor
public class NotificationService {

	private final NotificationRepository notificationRepository;
	private final MemberService memberService;
	private final SimpMessagingTemplate messagingTemplate;

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
		MemberDetails me = memberService.checkAuthentication(auth);

		Notification notification = getNotificationById(notificationId);
		// 남의 알림을 번호만으로 읽음 처리하지 못하게 한다
		if (!notification.getReceiver().getId().equals(me.getId())) {
			throw new NotificationException(NotificationErrorCode.NOT_FOUND_NOTIFICATION);
		}
		notification.markAsRead();
	}

	public static final String SYNC_DESTINATION = "/queue/notification-sync";

	/**
	 * 방을 열어 메시지를 봤으면 그 방에서 온 내 멘션 알림을 읽음 처리한다 (슬랙과 같은 동작).
	 * 읽은 게 있으면 내 다른 창·기기에도 알려 안읽음 숫자를 맞춘다.
	 * @return 읽음 처리한 개수
	 */
	@Transactional
	public int readMentionsInRoom(Authentication auth, Long roomId) {
		MemberDetails me = memberService.checkAuthentication(auth);
		int count = notificationRepository.markReadByRoom(me.getId(), roomId, NotificationType.MENTION);
		if (count > 0) {
			String username = me.getUsername();
			Runnable notify = () -> messagingTemplate.convertAndSendToUser(username, SYNC_DESTINATION,
				Map.of("type", "READ_ROOM", "roomId", roomId, "count", count));
			// 다른 창이 다시 불러올 때 바뀐 값이 보이도록 커밋 뒤에 알린다
			if (TransactionSynchronizationManager.isSynchronizationActive()) {
				TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
					@Override
					public void afterCommit() {
						notify.run();
					}
				});
			} else {
				notify.run();
			}
		}
		return count;
	}
}
