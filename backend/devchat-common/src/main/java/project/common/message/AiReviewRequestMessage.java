package project.common.message;

public record AiReviewRequestMessage(
        Long aiReviewId,
        Long chatRoomId,
        Long repoId,
        String filePath,
        String fileDiff,
        String fileContent,
        String baseContent,
        String prTitle,
        String prBody
) {}