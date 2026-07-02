package project.common.message.index;

public record RepoIndexRequestMessage(Long roomId, String repositoryUrl, Long memberId) {}