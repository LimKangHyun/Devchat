package project.common.message.aireview;

import project.common.dto.InlineReview;
import java.util.List;

/**
 * @param commitSha 이 결과를 만든 요청의 커밋. 그사이 새 커밋으로 리뷰가 다시 시작됐으면 API 서버가 이 결과를 버린다.
 *                  이 필드가 없던 이전 메시지는 null로 읽히고, 그때는 커밋 검사를 건너뛴다.
 */
public record AiReviewResultMessage(
        Long aiReviewId,
        Long chatRoomId,
        String filePath,
        String status,
        List<InlineReview> reviews,
        String errorMessage,
        String commitSha
) {}
