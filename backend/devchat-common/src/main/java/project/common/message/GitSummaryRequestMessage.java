package project.common.message;

public record GitSummaryRequestMessage(
    Long roomId,
    String eventType,    // ISSUE, PULL_REQUEST, WORKFLOW_RUN, PUSH
    String prStatus,     // PR일 때만 사용
    String fullContent
) {}