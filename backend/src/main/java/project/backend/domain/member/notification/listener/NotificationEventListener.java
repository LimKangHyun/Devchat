package project.backend.domain.member.notification.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import project.backend.domain.member.friend.dto.event.FriendEvent;
import project.backend.domain.member.notification.dto.AlertTemplate;
import project.backend.domain.member.notification.entity.NotificationType;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

	private final SimpMessagingTemplate simpMessagingTemplate;

	@Async
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void handleFriendRequest(FriendEvent event) {
		log.info("친구요청 이벤트 수신: {} => {}", event.senderUsername(), event.receiverUsername());

		NotificationType type = event.type();

		AlertTemplate alertTemplate = switch (type) {
			case FRIEND_REQUESTED -> AlertTemplate.fromFriendEvent(event);
			case FRIEND_ACCEPTED -> null;
			case CODE_REVIEW -> null;
		};

		simpMessagingTemplate.convertAndSend("/topic/notifications/" + event.receiverUsername(),
			alertTemplate);
	}


}
