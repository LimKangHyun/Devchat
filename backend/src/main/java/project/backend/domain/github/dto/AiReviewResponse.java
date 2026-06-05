package project.backend.domain.github.dto;

public record AiReviewResponse(String reviewJson, boolean githubPublished) {}