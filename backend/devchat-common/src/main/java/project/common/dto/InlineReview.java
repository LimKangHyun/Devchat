package project.common.dto;

public record InlineReview(
        int lineNumber,
        int diffLine,
        String comment
) {}