package project.common.message;

public record RepoIndexMessage(Long roomId, String repositoryUrl, Long memberId) {}