package project.api.domain.aireview.dto;

import java.util.List;
import java.util.Map;

public record AiReviewResponse(
        Map<String, List<AiReviewCommentResponse>> files,
        boolean githubPublished,
        String publishedBy,
        String prTitle,
        String prBody
) {}