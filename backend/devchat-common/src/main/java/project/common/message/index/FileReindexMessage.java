package project.common.message.index;

public record FileReindexMessage(
    Long roomId,
    String repoUrl,
    Long memberId,
    String filePath,
    String status,      // added / modified / removed
    String fileContent,
    String headSha
) {}