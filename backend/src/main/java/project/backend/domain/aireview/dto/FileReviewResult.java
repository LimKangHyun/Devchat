package project.backend.domain.aireview.dto;

import java.util.List;

public record FileReviewResult(
        String filePath,
        String baseContent,
        String fileContent,
        List<InlineReview> reviews,
        boolean skipped
) {}