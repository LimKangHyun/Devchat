package project.api.domain.notification.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import project.api.domain.notification.dto.NotificationDto;
import project.api.domain.notification.app.NotificationService;

@Tag(name = "Notification", description = "알림 API")
@RestController
@RequestMapping("/notification")
@RequiredArgsConstructor
public class NotificationsController {

	private final NotificationService notificationService;

	@Operation(summary = "알림 목록 조회")
	@GetMapping
	@ResponseStatus(HttpStatus.OK)
	public Page<NotificationDto> getNotifications(Authentication auth, Pageable pageable) {
		return notificationService.getNotifications(auth, pageable);
	}

	@Operation(summary = "읽지 않은 알림 조회")
	@GetMapping("/unread")
	@ResponseStatus(HttpStatus.OK)
	public Page<NotificationDto> getUnreadNotifications(Authentication auth,
		Pageable pageable) {
		return notificationService.getNotReadNotification(auth, pageable);
	}

	@Operation(summary = "알림 읽음 처리")
	@PostMapping("/read/{notificationId}")
	@ResponseStatus(HttpStatus.OK)
	public void readNotification(@PathVariable Long notificationId, Authentication auth) {
		notificationService.readNotification(notificationId, auth);
	}
}
