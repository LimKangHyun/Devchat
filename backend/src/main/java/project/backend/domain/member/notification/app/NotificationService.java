package project.backend.domain.member.notification.app;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.backend.auth.dto.MemberDetails;
import project.backend.domain.member.entity.Member;
import project.backend.domain.member.notification.dao.NotificationRepository;
import project.backend.domain.member.notification.dto.AlertTemplate;
import project.backend.global.exception.errorcode.AuthErrorCode;
import project.backend.global.exception.ex.AuthException;

@Service
@RequiredArgsConstructor
public class NotificationService {

	private final NotificationRepository notificationRepository;

	@Transactional(readOnly = true)
	public Page<AlertTemplate> getNotifications(Authentication auth, Pageable pageable) {
		MemberDetails memberDetails = (MemberDetails) auth.getPrincipal();
		if (memberDetails == null) {
			throw new AuthException(AuthErrorCode.UNAUTHORIZED_USER);
		}

		Member receiver = MemberDetails.of(memberDetails);
		return notificationRepository.getNotificationsAndReadNot(receiver.getId(), pageable);
	}
}
