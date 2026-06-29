package project.common.dto;

import java.util.List;

public record FileReviewResult(
        String filePath,
        String baseContent,
        String fileContent,
        List<InlineReview> reviews,
        boolean skipped
) {}