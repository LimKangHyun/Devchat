package project.common.message;

public record GitSummaryResultMessage(
    Long roomId,
    String summarizedContent
) {}