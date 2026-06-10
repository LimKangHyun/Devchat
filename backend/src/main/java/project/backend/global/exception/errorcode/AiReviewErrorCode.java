package project.backend.global.exception.errorcode;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum AiReviewErrorCode implements ErrorCode {

    AI_REVIEW_NOT_FOUND("AI-001", "AI 리뷰를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    COMMENT_NOT_FOUND("AI-002", "코멘트를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    COMMENT_STATUS_NOT_FOUND("AI-003", "코멘트 상태를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    ALREADY_PUBLISHED("AI-004", "이미 GitHub에 등록된 리뷰입니다.", HttpStatus.CONFLICT),
    PR_NOT_OPEN("AI-005", "종료된 PR에는 GitHub 리뷰를 등록할 수 없습니다.", HttpStatus.UNPROCESSABLE_ENTITY),
    NO_REVIEWS("AI-006", "등록할 AI 리뷰가 없습니다.", HttpStatus.UNPROCESSABLE_ENTITY),
    NO_ACTIVE_REVIEWS("AI-007", "활성화된 AI 리뷰가 없습니다.", HttpStatus.UNPROCESSABLE_ENTITY),
    INACTIVE_REASON_REQUIRED("AI-008", "비활성화 시 사유는 필수입니다.", HttpStatus.BAD_REQUEST),
    OTHER_REASON_REQUIRED("AI-009", "기타 사유를 입력해주세요.", HttpStatus.BAD_REQUEST),
    REVIEW_JSON_PARSE_FAILED("AI-010", "리뷰 JSON 파싱에 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    REVIEW_JSON_SERIALIZE_FAILED("AI-011", "리뷰 JSON 직렬화에 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus status;
}