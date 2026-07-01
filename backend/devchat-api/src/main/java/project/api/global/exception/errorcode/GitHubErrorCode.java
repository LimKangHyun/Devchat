package project.api.global.exception.errorcode;

import lombok.AllArgsConstructor;
import lombok.Getter;
import project.common.exception.errorcode.ErrorCode;

@Getter
@AllArgsConstructor
public enum GitHubErrorCode implements ErrorCode {

    INVALID_REPO_RUL("GE-001", "잘못된 GitHub Repository URL 입니다.", 400),
    UNAUTHORIZED_REPO("GE-002", "해당 GitHub Repository에 권한이 없습니다.", 401),
    INVALID_TOKEN("GE-002", "토큰이 유효하지 않습니다.", 401),
    REPO_NOT_FOUND("GE-003", "리포지토리를 찾을 수 없거나 접근 권한이 없습니다. (private 리포지토리는 접근할 수 없음)",
        404), //todo TokenErrorCode로 변경
    CLIENT_ERROR("GE-004", "잘못된 요청입니다. API rate limit 등을 확인해주세요.", 400),
    SERVER_ERROR("GE-005", "GitHub 서버에 오류가 발생했습니다.", 500),
    UNEXPECTED_RESPONSE("GE-006", "요청한 리포지토리의 permissions에 관한 정보가 없습니다.",
        500),
    WEBHOOK_REGISTER_FAILED("GE-007", "웹훅 등록 중 예상치 못한 예외가 발생했습니다.",
        500),
    WEBHOOK_DELETE_FAILED("GE-008", "웹훅 삭제 중 예상치 못한 예외가 발생했습니다.",
        500),
    INVALID_PR_ACTION("GE-009", "알 수 없는 PR 액션입니다.", 400),
    INVALID_REPO_URL("GE-010", "유효하지 않은 저장소 URL입니다.", 400),
    GITHUB_API_FAILED("GE-011", "GitHub API 호출에 실패했습니다.", 502);

    private final String code;
    private final String message;
    private final int status;
}