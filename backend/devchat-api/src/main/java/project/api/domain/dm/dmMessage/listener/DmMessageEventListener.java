package project.api.domain.dm.dmMessage.listener;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import project.api.domain.dm.dmMessage.app.DmMessageService;
import project.api.domain.dm.dmMessage.dto.DmMessageResponse;

@Component
@RequiredArgsConstructor
public class DmMessageEventListener {

	private final DmMessageService dmMessageService;
	private final SimpMessagingTemplate messagingTemplate;

	@Async
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void handleDmMessageSent(DmMessageResponse dmEvent) {
		DmMessageResponse response = dmMessageService.saveEvent(dmEvent);

		messagingTemplate.convertAndSend("/topic/dm/" + response.roomId(), response);
	}

}
