package project.backend.global.exception.errorcode;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum GitHubErrorCode implements ErrorCode {

    INVALID_REPO_RUL("GE-001", "잘못된 GitHub Repository URL 입니다.", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED_REPO("GE-002", "해당 GitHub Repository에 권한이 없습니다.", HttpStatus.UNAUTHORIZED),
    INVALID_TOKEN("TE-002", "토큰이 유효하지 않습니다.", HttpStatus.UNAUTHORIZED),
    REPO_NOT_FOUND("GE-003", "리포지토리를 찾을 수 없거나 접근 권한이 없습니다. (private 리포지토리는 접근할 수 없음)",
        HttpStatus.NOT_FOUND), //todo TokenErrorCode로 변경
    CLIENT_ERROR("GE-004", "잘못된 요청입니다. API rate limit 등을 확인해주세요.", HttpStatus.BAD_REQUEST),
    SERVER_ERROR("GE-005", "GitHub 서버에 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    UNEXPECTED_RESPONSE("GE-006", "요청한 리포지토리의 permissions에 관한 정보가 없습니다.",
        HttpStatus.INTERNAL_SERVER_ERROR),
    WEBHOOK_REGISTER_FAILED("GE-007", "웹훅 등록 중 예상치 못한 예외가 발생했습니다.",
        HttpStatus.INTERNAL_SERVER_ERROR),
    WEBHOOK_DELETE_FAILED("GE-008", "웹훅 삭제 중 예상치 못한 예외가 발생했습니다.",
        HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
