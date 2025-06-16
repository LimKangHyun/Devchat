package project.backend.domain.member.notification.api;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import project.backend.domain.member.friend.dto.event.FriendEvent;
import project.backend.domain.member.notification.app.NotificationService;

@RestController
@RequestMapping("/notification")
@RequiredArgsConstructor
public class NotificationsController {

	private final NotificationService notificationService;

	@GetMapping
	@ResponseStatus(HttpStatus.OK)
	public Page<FriendEvent> getNotifications(Authentication auth, Pageable pageable) {
		return notificationService.getNotifications(auth, pageable);
	}
}
