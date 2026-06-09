package project.backend.domain.aireview.event;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import project.backend.domain.aireview.app.AiReviewService;

@Component
@RequiredArgsConstructor
public class AiReviewEventListener {

    private final AiReviewService aiReviewService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("chatBroadcastExecutor")
    public void handleAiReviewRequested(AiReviewRequestedEvent event) {
        aiReviewService.triggerAiReview(event.room(), event.prNumber(), event.headSha(), event.baseSha());
    }
}