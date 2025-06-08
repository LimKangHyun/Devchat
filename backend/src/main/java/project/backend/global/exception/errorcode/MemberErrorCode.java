package project.backend.global.exception.errorcode;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum MemberErrorCode implements ErrorCode {
	USERNAME_ALREADY_EXISTS("ME-001", "이미 사용 중인 ID입니다.", HttpStatus.CONFLICT),
	EMAIL_ALREADY_EXISTS("ME-002", "이미 사용 중인 이메일입니다.", HttpStatus.CONFLICT),
	MEMBER_NOT_FOUND("ME-002", "사용자 정보를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
	WRONG_PASSWORD("ME-003", "현재 비밀번호가 일치하지 않습니다.", HttpStatus.BAD_REQUEST),
	SAME_AS_OLD_PASSWORD("ME-004", "새로운 비밀번호는 기존 비밀번호와 달라야 합니다.", HttpStatus.BAD_REQUEST);

	private final String code;
	private final String message;
	private final HttpStatus status;

}
