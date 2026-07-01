package project.api.domain.aireview.dto;

public record AiReviewResponse(
        String reviewJson,
        boolean githubPublished,
        String publishedBy,
        String prTitle,
        String prBody
) {}