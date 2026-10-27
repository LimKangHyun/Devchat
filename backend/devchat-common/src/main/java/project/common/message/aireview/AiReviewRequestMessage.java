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
    List<String> changedFilesInPr,   // 이번 PR에서 함께 변경된 전체 파일 경로 목록 (자기 자신 포함)
    List<ChangedFileSnapshot> relatedChangedFiles,   // 이 파일이 참조하는 다른 변경 파일의 변경 후 코드. 없으면 null일 수 있다
    String reviewConventions,   // 방장이 정한 팀 리뷰 규칙. 없으면 null (이 필드가 없던 이전 메시지도 null로 읽힌다)
    String commitSha   // 리뷰 대상 커밋. 새 커밋으로 리뷰가 다시 시작되면, 예전 커밋의 요청·결과를 구분해 버리는 데 쓴다
) {}