package project.api.domain.aireview.event;

public record AiReviewRequestedEvent(Long aiReviewId, String headSha, String baseSha) {}