package project.common.message.git;

public record GitSummaryResultMessage(
    Long roomId,
    Long messageId,
    String summarizedContent
) {}