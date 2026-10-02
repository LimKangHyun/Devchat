package project.common.message.index;

public record FileReindexMessage(
    Long roomId,
    String repoUrl,
    Long memberId,
    String filePath,
    String status,      // added / modified / removed / renamed
    String fileContent,
    String headSha,
    String previousFilePath   // renamed일 때만 옛 경로, 그 외 null
) {}