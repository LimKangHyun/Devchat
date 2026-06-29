package project.common.exception.errorcode;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum IndexingErrorCode implements ErrorCode {
    EMBEDDING_EXHAUSTED("IDX-003", "모든 임베딩 API 키가 소진되었습니다.", 503),
    EMBEDDING_INVALID_REQUEST("IDX-004", "임베딩 요청이 잘못되었습니다.", 400),
    EMBEDDING_FAILED("IDX-005", "임베딩 처리에 실패했습니다.", 500);

    private final String code;
    private final String message;
    private final int status;
}