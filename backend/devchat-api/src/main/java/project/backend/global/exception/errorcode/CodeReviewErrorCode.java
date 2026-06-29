package project.backend.global.exception.errorcode;

import lombok.AllArgsConstructor;
import lombok.Getter;
import project.common.exception.errorcode.ErrorCode;

@Getter
@AllArgsConstructor
public enum CodeReviewErrorCode implements ErrorCode {
	REVIEW_NOT_FOUND("CRE-001", "리뷰를 찾을 수 없습니다", 404),
	UNAUTHORIZED_ACCESS("CRE-002", "리뷰 작성자만 수정 및 삭제에 접근가능합니다.", 403),
	INVALID_MESSAGE_TYPE("CRE-003", "메세지 타입이 CODE인 경우만 리뷰작성이 가능합니다.", 429);
	private final String code;
	private final String message;
	private final int status;
}