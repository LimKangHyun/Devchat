package project.api.domain.aireview.dto;

public record AiReviewCommentResponse(
        Long commentId,
        String filePath,
        int lineNumber,
        int diffLine,
        String comment,
        boolean active
) {}