package project.backend.global.exception.errorcode;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum CodeReviewErrorCode implements ErrorCode{
	REVIEW_NOT_FOUND("CRE-001", "리뷰를 찾을 수 없습니다", HttpStatus.NOT_FOUND),
	UNAUTHORIZED_ACCESS("CRE-002", "리뷰 작성자만 수정 및 삭제에 접근가능합니다.", HttpStatus.FORBIDDEN),
	INVALID_MESSAGE_TYPE("CRE-003", "메세지 타입이 CODE인 경우만 리뷰작성이 가능합니다.", HttpStatus.BAD_REQUEST);
	private final String code;
	private final String message;
	private final HttpStatus status;
}
