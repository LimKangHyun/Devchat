package project.common.message.index;

/**
 * 인덱싱 진행률.
 *
 * @param phase 진행 단계. CLONING(내려받기) → CHUNKING(코드 분석, 단위: 파일) → EMBEDDING(저장, 단위: 코드 조각)
 * @param done  현재 단계에서 처리한 개수
 * @param total 현재 단계의 전체 개수. 모르면 0
 */
public record IndexingProgressMessage(Long roomId, String phase, int done, int total) {

    public static final String CLONING = "CLONING";
    public static final String CHUNKING = "CHUNKING";
    public static final String EMBEDDING = "EMBEDDING";
}
