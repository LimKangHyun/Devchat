package project.backend.domain.dm.dmMessage.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import project.backend.domain.dm.dmMessage.app.DmMessageService;
import project.backend.domain.dm.dmMessage.dto.DmMessageRequest;
import project.backend.domain.dm.dmMessage.dto.DmMessageResponse;

@Slf4j
@RestController
@RequestMapping("/dm")
@RequiredArgsConstructor
public class DmMessageController {

	private final DmMessageService dmMessageService;

	@MessageMapping("/send/{roomId}")
	public DmMessageResponse sendMessage(@DestinationVariable Long roomId,
		@Payload DmMessageRequest request, Authentication authentication) {
		return dmMessageService.save(roomId, request, authentication);
	}

	@GetMapping("/history/{roomId}")
	public Page<DmMessageResponse> getDmMessages(@PathVariable Long roomId,
		Authentication auth,
		Pageable pageable
	) {
		return dmMessageService.getDmMessages(roomId, pageable, auth);
	}
}
