package project.backend.domain.chat.github.api;

import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import project.backend.domain.chat.github.app.GitMessageService;

@Tag(name = "GitHub Webhook", description = "GitHub 이벤트 Webhook API")
@Slf4j
@RestController
@RequestMapping("/github")
@RequiredArgsConstructor
public class GitWebhookController {

	private final GitMessageService gitMessageService;

	@Operation(
			summary = "GitHub Webhook 수신",
			description = "GitHub Push / PR / Issue 이벤트를 수신하여 채팅 메시지로 변환"
	)
	@PostMapping("/{roomId}")
	public void handleWebhook(@PathVariable Long roomId,
		@RequestBody Map<String, Object> payload,
		@RequestHeader("X-GitHub-Event") String eventType) {
		log.info("[WEBHOOK] 📬 Webhook received - roomId: {}", roomId);
		gitMessageService.handleEvent(roomId, eventType, payload);
	}

}
