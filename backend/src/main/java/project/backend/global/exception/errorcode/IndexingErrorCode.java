package project.backend.global.exception.errorcode;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum IndexingErrorCode implements ErrorCode {

    EMBEDDING_EXHAUSTED("IDX-003", "모든 임베딩 API 키가 소진되었습니다.", HttpStatus.SERVICE_UNAVAILABLE),
    EMBEDDING_INVALID_REQUEST("IDX-004", "임베딩 요청이 잘못되었습니다.", HttpStatus.BAD_REQUEST),
    EMBEDDING_FAILED("IDX-005", "임베딩 처리에 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus status;
}