package project.api.domain.github.api;

import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import project.api.auth.dto.MemberDetails;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import project.api.domain.github.app.GitMessageService;
import project.api.domain.github.webhook.GitHubWebhookReceiver;

@Tag(name = "GitHub Webhook", description = "GitHub 이벤트 Webhook API")
@Slf4j
@RestController
@RequestMapping("/github")
@RequiredArgsConstructor
public class GitWebhookController {

	private final GitMessageService gitMessageService;
	private final GitHubWebhookReceiver gitHubWebhookReceiver;

	/**
	 * 서명 검증에는 GitHub가 보낸 본문 원문이 필요해서, JSON으로 바로 받지 않고 바이트로 받는다.
	 * 위조(서명 불일치)면 401, 이미 처리한 전달이면 200으로 응답해 GitHub가 다시 보내지 않게 한다.
	 */
	@Operation(
			summary = "GitHub Webhook 수신",
			description = "GitHub Push / PR / Issue / Workflow 이벤트를 수신하여 채팅 메시지로 변환. X-Hub-Signature-256 서명을 검증한다."
	)
	@PostMapping("/webhook/{roomId}")
	public ResponseEntity<Void> handleWebhook(@PathVariable Long roomId,
		@RequestBody byte[] body,
		@RequestHeader("X-GitHub-Event") String eventType,
		@RequestHeader(value = "X-GitHub-Delivery", required = false) String deliveryId,
		@RequestHeader(value = "X-Hub-Signature-256", required = false) String signature) {
		log.info("[WEBHOOK] Webhook received - roomId={}, event={}, delivery={}", roomId, eventType, deliveryId);
		GitHubWebhookReceiver.Outcome outcome =
			gitHubWebhookReceiver.receive(roomId, eventType, deliveryId, signature, body);
		return outcome == GitHubWebhookReceiver.Outcome.REJECTED
			? ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
			: ResponseEntity.ok().build();
	}

	@Operation(
			summary = "AI 리뷰 GitHub 등록",
			description = "DevChat AI 리뷰를 GitHub PR에 공식 등록"
	)
	@PostMapping("/{roomId}/ai-review/{aiReviewId}/publish")
	public void publishAiReview(
			@PathVariable Long roomId,
			@PathVariable Long aiReviewId,
			@AuthenticationPrincipal MemberDetails memberDetails) {
		gitMessageService.publishAiReview(roomId, aiReviewId, memberDetails.getUsername());
	}

	@PostMapping("/{roomId}/ai-review/retry")
	public void retryAiReview(@PathVariable Long roomId,
							  @RequestBody Map<String, Object> body) {
		int prNumber = (int) body.get("prNumber");
		gitMessageService.retryAiReview(roomId, prNumber);
	}
}