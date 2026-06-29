package project.common.message;

public record RepoIndexingMessage(Long roomId, String repositoryUrl, Long memberId) {}