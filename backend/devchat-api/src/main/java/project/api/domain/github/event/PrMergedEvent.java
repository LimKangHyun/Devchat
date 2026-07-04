package project.api.domain.github.event;

public record PrMergedEvent(Long roomId, int prNumber, String headSha) {}