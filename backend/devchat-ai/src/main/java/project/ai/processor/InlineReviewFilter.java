package project.ai.processor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import project.common.dto.InlineReview;

import java.util.List;

/**
 * AI가 생성한 인라인 리뷰 결과 중 유효한 것만 걸러낸다.
 * InlineReview 도메인 타입에 의존하는 검증 로직.
 */
@Slf4j
@Component
public class InlineReviewFilter {

    public List<InlineReview> filterValidReviews(List<InlineReview> reviews, String fileContent) {
        int totalLines = fileContent.split("\n").length;

        return reviews.stream()
                .filter(review -> {
                    if (review.lineNumber() == null || review.lineNumber() < 1 || review.lineNumber() > totalLines) {
                        log.warn("lineNumber 범위 초과 제거: lineNumber={}, totalLines={}", review.lineNumber(), totalLines);
                        return false;
                    }
                    if (review.comment() == null || review.comment().isBlank()) {
                        log.warn("빈 comment 제거: lineNumber={}", review.lineNumber());
                        return false;
                    }
                    return true;
                })
                .toList();
    }
}