package project.backend.global.exception.errorcode;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum TokenErrorCode implements ErrorCode {
	INVALID_TOKEN("TE-001", "로그인 해주세요"),
	EXPIRED_TOKEN("TE-002", "세션이 만료되었습니다. 다시 로그인해주세요."),
	UNKNOWN_ERROR("TE-003", "시스템 오류, 다시 로그인해주세요"),
	NOT_FOUND_TOKEN("TE-004", "로그인 해주세요.");

	private final String code;
	private final String message;
	private final HttpStatus status = HttpStatus.UNAUTHORIZED;

}
