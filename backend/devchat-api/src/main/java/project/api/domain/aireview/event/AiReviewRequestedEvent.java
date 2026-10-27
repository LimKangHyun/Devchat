package project.api.domain.aireview.event;

import java.util.Set;

/**
 * @param reusedFiles 이전 커밋의 리뷰 결과를 그대로 쓰는 파일(증분 리뷰). 이 파일들은 다시 리뷰하지 않는다
 */
public record AiReviewRequestedEvent(Long aiReviewId, String headSha, String baseSha, Set<String> reusedFiles) {

    public AiReviewRequestedEvent(Long aiReviewId, String headSha, String baseSha) {
        this(aiReviewId, headSha, baseSha, Set.of());
    }
}
