package project.api.domain.aireview.outbox;

import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import project.api.domain.aireview.app.AiReviewService;
import project.api.global.outbox.OutboxEventType;
import project.api.global.outbox.OutboxService;
import project.common.message.aireview.AiReviewRequestMessage;

/** AI 리뷰 쪽에서 outbox에 이벤트를 적는 곳 */
@Component
@RequiredArgsConstructor
public class AiReviewOutbox {

    private final OutboxService outboxService;
    private final AiReviewService aiReviewService;

    /**
     * 리뷰 준비를 예약한다. 리뷰를 만들거나 리셋한 트랜잭션 안에서 불러야 둘이 함께 커밋된다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueuePrepare(Long aiReviewId, String headSha, String baseSha, Set<String> reusedFiles) {
        outboxService.enqueue(OutboxEventType.AI_REVIEW_PREPARE, aiReviewId,
            new AiReviewPreparePayload(aiReviewId, headSha, baseSha, reusedFiles));
    }

    /**
     * 준비 결과를 한 트랜잭션에 저장한다: 총 파일 수, 화면용 파일 코드, 파일별 리뷰 요청.
     * 예전에는 요청을 하나씩 Redis로 보내다 중간에 실패하면 일부 파일만 요청되고 총 파일 수와 어긋났다.
     */
    @Transactional
    public void savePrepared(Long aiReviewId, List<AiReviewRequestMessage> requests,
                             Map<String, String> fileContents, Set<String> reusedFiles) {
        aiReviewService.updateTotalFiles(aiReviewId, requests.size());
        aiReviewService.saveFileContents(aiReviewId, fileContents, reusedFiles);
        for (AiReviewRequestMessage request : requests) {
            outboxService.enqueue(OutboxEventType.AI_REVIEW_REQUEST, aiReviewId, request);
        }
    }
}
