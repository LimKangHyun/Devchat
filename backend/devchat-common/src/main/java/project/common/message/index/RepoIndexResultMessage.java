package project.common.message.index;

public record RepoIndexResultMessage(Long roomId, boolean success, String errorMessage) {}