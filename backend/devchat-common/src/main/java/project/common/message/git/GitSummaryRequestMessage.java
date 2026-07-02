package project.common.message.git;

public record GitSummaryRequestMessage(
    Long roomId,
    Long messageId,
    String eventType,    // ISSUE, PULL_REQUEST, WORKFLOW_RUN, PUSH
    String prStatus,     // PR일 때만 사용
    String fullContent
) {}