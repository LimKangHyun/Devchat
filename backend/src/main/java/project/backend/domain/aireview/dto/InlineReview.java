package project.backend.domain.aireview.dto;

public record InlineReview(
        int lineNumber,
        String comment
) {}