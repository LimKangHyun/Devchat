package project.common.message.aireview;

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