package project.backend.global.exception.errorcode;

import lombok.AllArgsConstructor;
import lombok.Getter;
import project.common.exception.errorcode.ErrorCode;

@Getter
@AllArgsConstructor
public enum MemberErrorCode implements ErrorCode {
	USERNAME_ALREADY_EXISTS("ME-001", "이미 사용 중인 ID입니다.", 409),
	EMAIL_ALREADY_EXISTS("ME-002", "이미 사용 중인 이메일입니다.", 409),
	MEMBER_NOT_FOUND("ME-002", "사용자 정보를 찾을 수 없습니다.", 404),
	WRONG_PASSWORD("ME-003", "현재 비밀번호가 일치하지 않습니다.", 400),
	SAME_AS_OLD_PASSWORD("ME-004", "새로운 비밀번호는 기존 비밀번호와 달라야 합니다.", 400);

	private final String code;
	private final String message;
	private final int status;

}