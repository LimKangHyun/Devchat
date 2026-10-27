package project.api.domain.aireview.outbox;

import java.util.Set;

/**
 * AI 리뷰 준비 이벤트 내용.
 *
 * @param reusedFiles 이전 커밋의 리뷰 결과를 그대로 쓰는 파일(증분 리뷰). 다시 리뷰하지 않는다
 */
public record AiReviewPreparePayload(Long aiReviewId, String headSha, String baseSha, Set<String> reusedFiles) {

    public AiReviewPreparePayload {
        reusedFiles = reusedFiles == null ? Set.of() : Set.copyOf(reusedFiles);
    }
}
