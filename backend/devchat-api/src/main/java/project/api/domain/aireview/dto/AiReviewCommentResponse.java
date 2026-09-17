package project.api.domain.aireview.dto;

public record AiReviewCommentResponse(
        Long commentId,
        String filePath,
        int lineNumber,
        Integer diffLine,
        String comment,
        boolean active
) {}