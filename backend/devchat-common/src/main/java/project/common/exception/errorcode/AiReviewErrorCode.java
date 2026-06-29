package project.common.exception.errorcode;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum AiReviewErrorCode implements ErrorCode {
    AI_REVIEW_NOT_FOUND("AI-001", "AI 리뷰를 찾을 수 없습니다.", 404),
    COMMENT_NOT_FOUND("AI-002", "코멘트를 찾을 수 없습니다.", 404),
    COMMENT_STATUS_NOT_FOUND("AI-003", "코멘트 상태를 찾을 수 없습니다.", 404),
    ALREADY_PUBLISHED("AI-004", "이미 GitHub에 등록된 리뷰입니다.", 409),
    PR_NOT_OPEN("AI-005", "종료된 PR에는 GitHub 리뷰를 등록할 수 없습니다.", 422),
    NO_REVIEWS("AI-006", "등록할 AI 리뷰가 없습니다.", 422),
    NO_ACTIVE_REVIEWS("AI-007", "활성화된 AI 리뷰가 없습니다.", 422),
    INACTIVE_REASON_REQUIRED("AI-008", "비활성화 시 사유는 필수입니다.", 400),
    OTHER_REASON_REQUIRED("AI-009", "기타 사유를 입력해주세요.", 400),
    REVIEW_JSON_PARSE_FAILED("AI-010", "리뷰 JSON 파싱에 실패했습니다.", 500),
    REVIEW_JSON_SERIALIZE_FAILED("AI-011", "리뷰 JSON 직렬화에 실패했습니다.", 500);

    private final String code;
    private final String message;
    private final int status;
}