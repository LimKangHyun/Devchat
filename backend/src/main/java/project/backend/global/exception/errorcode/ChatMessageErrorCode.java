package project.backend.global.exception.errorcode;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ChatMessageErrorCode implements ErrorCode {

    INVALID_KEYWORD_LENGTH("CME-001", "검색어는 최소 2자 이상이어야 합니다.",
        HttpStatus.BAD_REQUEST),
    INVALID_ROUTE("CME-002", "유효하지 않은 경로입니다.", HttpStatus.BAD_REQUEST),
    MESSAGE_NOT_FOUND("CME-003", "메세지를 찾을 수 없습니다.", HttpStatus.BAD_REQUEST),
    TOO_MANY_REQUESTS("CME-004", "메시지를 너무 빠르게 보내고 있어요. 잠시 후 다시 시도해주세요.", HttpStatus.TOO_MANY_REQUESTS);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
