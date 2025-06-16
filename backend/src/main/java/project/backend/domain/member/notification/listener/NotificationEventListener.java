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

		NotificationType type = event.type();

		switch (type) {
			case FRIEND_REQUESTED -> {
				log.info("친구 요청 알림: {} → {}", event.senderUsername(), event.receiverUsername());
			}
			case FRIEND_ACCEPTED -> {
				log.info("친구 수락 알림: {} → {}", event.senderUsername(), event.receiverUsername());
			}
			case CODE_REVIEW -> {
				log.warn("CODE_REVIEW 알림은 아직 지원되지 않음: {} → {}", event.senderUsername(),
					event.receiverUsername());
			}
		}

		simpMessagingTemplate.convertAndSend("/topic/notifications/" + event.receiverUsername(),
			event);
	}

}
