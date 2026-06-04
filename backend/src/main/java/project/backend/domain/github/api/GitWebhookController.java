package project.backend.domain.github.api;

import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import project.backend.domain.github.app.GitMessageService;
import project.backend.domain.github.dto.AiReviewResponse;

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

	@Operation(
			summary = "AI 리뷰 GitHub 등록",
			description = "DevChat AI 리뷰를 GitHub PR에 공식 등록"
	)
	@PostMapping("/{roomId}/ai-review/{messageId}/publish")
	public void publishAiReview(@PathVariable Long roomId,
								@PathVariable Long messageId) {
		gitMessageService.publishAiReview(roomId, messageId);
	}

	@PostMapping("/{roomId}/ai-review/retry")
	public void retryAiReview(@PathVariable Long roomId,
							  @RequestBody Map<String, Object> body) {
		int prNumber = (int) body.get("prNumber");
		gitMessageService.retryAiReview(roomId, prNumber);
	}

}
