package project.backend.domain.chat.github.controller;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import project.backend.domain.chat.github.app.GitMessageService;

@Slf4j
@RestController
@RequestMapping("/github")
@RequiredArgsConstructor
public class GitWebhookController {

	private final GitMessageService gitMessageService;

	@PostMapping("/{roomId}")
	public void handleWebhook(@PathVariable Long roomId,
		@RequestBody Map<String, Object> payload,
		@RequestHeader("X-GitHub-Event") String eventType) {
		log.info("[WEBHOOK] 📬 Webhook received - roomId: {}", roomId);
		gitMessageService.handleEvent(roomId, eventType, payload);
	}

}
