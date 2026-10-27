package project.common.message.aireview;

/**
 * 같은 PR에서 함께 변경된 다른 파일의 변경 후 모습.
 * 리뷰는 파일마다 따로 돌기 때문에, 리뷰 대상 파일이 호출하는 다른 변경 파일의 새 코드를 함께 넘기는 데 쓴다.
 * 인덱스에는 변경 전 코드만 있어서 RAG로는 이 코드를 가져올 수 없다.
 */
public record ChangedFileSnapshot(
    String filePath,
    String fileDiff,
    String fileContent
) {}
