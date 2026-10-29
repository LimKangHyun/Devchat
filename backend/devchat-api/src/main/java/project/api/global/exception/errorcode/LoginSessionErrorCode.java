package project.api.global.exception.errorcode;

import lombok.AllArgsConstructor;
import lombok.Getter;
import project.common.exception.errorcode.ErrorCode;

@Getter
@AllArgsConstructor
public enum LoginSessionErrorCode implements ErrorCode {
	NOT_FOUND("LS-001", "로그인 기기를 찾을 수 없어요.", 404),
	CANNOT_REVOKE_CURRENT("LS-002", "지금 쓰는 기기는 여기서 로그아웃할 수 없어요.", 400);

	private final String code;
	private final String message;
	private final int status;
}
