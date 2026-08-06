package project.common.message.aireview;

import java.util.List;

public record AiReviewRequestMessage(
    Long aiReviewId,
    Long chatRoomId,
    Long repoId,
    String filePath,
    String fileDiff,
    String fileContent,
    String baseContent,
    String prTitle,
    String prBody,
    List<String> changedFilesInPr   // 이번 PR에서 함께 변경된 전체 파일 경로 목록 (자기 자신 포함)
) {}