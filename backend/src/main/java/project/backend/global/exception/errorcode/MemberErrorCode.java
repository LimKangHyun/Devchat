package project.backend.global.exception.errorcode;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum MemberErrorCode implements ErrorCode {
	MEMBER_ALREADY_EXISTS("ME-001", "이미 사용 중인 ID입니다.", HttpStatus.CONFLICT),
	EMAIL_ALREADY_EXISTS("ME-002", "이미 사용 중인 이메일입니다.", HttpStatus.CONFLICT),
	MEMBER_NOT_FOUND("ME-002", "사용자 정보를 찾을 수 없습니다.", HttpStatus.NOT_FOUND);

	private final String code;
	private final String message;
	private final HttpStatus status;

}
