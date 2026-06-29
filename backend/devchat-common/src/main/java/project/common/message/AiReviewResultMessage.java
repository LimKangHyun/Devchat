package project.common.message;

import project.common.dto.InlineReview;
import java.util.List;

public record AiReviewResultMessage(
        Long aiReviewId,
        Long chatRoomId,
        String filePath,
        String status,
        List<InlineReview> reviews,
        String errorMessage
) {}