package project.backend.domain.member.notification.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import project.backend.domain.member.friend.dto.event.FriendRequestEvent;
import project.backend.domain.member.notification.dto.AlertTemplate;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

	private final SimpMessagingTemplate simpMessagingTemplate;

	@Async
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void handleFriendRequest(FriendRequestEvent event) {
		log.info("친구요청 이벤트 수신: {} => {}", event.senderUsername(), event.receiverUsername());

		AlertTemplate alertTemplate = AlertTemplate.of(event);

		simpMessagingTemplate.convertAndSend("/topic/notifications/" + event.receiverUsername(),
			alertTemplate);
	}
}
