package project.backend.global.exception.errorcode;

import lombok.AllArgsConstructor;
import lombok.Getter;
import project.common.exception.errorcode.ErrorCode;

@Getter
@AllArgsConstructor
public enum AuthErrorCode implements ErrorCode {

	UNAUTHORIZED_USER("AUTH-001", "로그인한 사용자만 접근할 수 있습니다.", 401),
	FORBIDDEN_PARTICIPANT("AUTH-002", "해당 채팅방에 참여 중인 사용자만 접근할 수 있습니다.", 403),
	UNSUPPORTED_PROVIDER("AUTH-003", "지원하지 않는 제공자 입니다.", 422),
	FORBIDDEN_MESSAGE_EDIT("AUTH-004", "본인이 전송한 메세지만 수정할 수 있습니다.", 403),
	FORBIDDEN_MESSAGE_DELETE("AUTH-005", "본인이 전송한 메세지만 삭제할 수 있습니다.", 403),
	WRONG_AUTH_TYPE_LOGIN("AUTH-006", "깃허브(OAuth)로 가입된 계정입니다. 깃허브 로그인을 이용해주세요.",
			401);

	private final String code;
	private final String message;
	private final int status;
}