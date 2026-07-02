package project.api.domain.github.event;

public record GitSummaryRequestEvent(
        Long roomId,
        Long messageId,
        String eventType,
        String prStatus,
        String fullContent
) {}