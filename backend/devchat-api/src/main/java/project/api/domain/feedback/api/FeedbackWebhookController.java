package project.api.domain.feedback.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import project.api.domain.feedback.app.FeedbackWebhookHandler;

/**
 * DevChat 저장소에 직접 등록하는 webhook (Settings → Webhooks, 이벤트: Issues·Issue comments).
 * 채팅방 webhook(/github/webhook/{roomId})과 같은 secret(GITHUB_WEBHOOK_SECRET)으로 서명을 검사한다.
 * 경로가 /github/webhook/** 라 로그인 없이 받는다(SecurityConfig).
 */
@Tag(name = "Feedback", description = "피드백 보내기")
@RestController
@RequiredArgsConstructor
public class FeedbackWebhookController {

    private final FeedbackWebhookHandler handler;

    @Operation(summary = "피드백 이슈 webhook 수신",
            description = "관리자가 피드백 이슈에 댓글을 달거나 이슈를 닫으면 보낸 사람에게 알린다. X-Hub-Signature-256 서명을 검증한다.")
    @PostMapping("/github/webhook/feedback")
    public ResponseEntity<Void> receive(@RequestBody byte[] body,
                                        @RequestHeader("X-GitHub-Event") String event,
                                        @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature) {
        FeedbackWebhookHandler.Outcome outcome = handler.receive(event, body, signature);
        return outcome == FeedbackWebhookHandler.Outcome.REJECTED
                ? ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
                : ResponseEntity.ok().build();
    }
}
