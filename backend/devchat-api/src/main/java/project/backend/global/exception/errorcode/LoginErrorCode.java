package project.backend.global.exception.errorcode;

import lombok.AllArgsConstructor;
import lombok.Getter;
import project.common.exception.errorcode.ErrorCode;

@Getter
@AllArgsConstructor
public enum LoginErrorCode implements ErrorCode {
	BAD_CREDENTIALS("LE-001", "ID 또는 비밀번호가 일치하지 않습니다. 다시 확인해 주십시오.", 401),
	DISABLED("LE-002", "계정이 비활성화 되었습니다. 관리자에게 문의하세요.", 401),
	CREDENTIALS_EXPIRED("LE-003", "비밀번호 유효기간이 만료 되었습니다. 관리자에게 문의하세요.", 401),
	UNKNOWN("LE-004", "알 수 없는 이유로 로그인에 실패하였습니다. 관리자에게 문의하세요.", 401);

	private final String code;
	private final String message;
	private final int status;
}