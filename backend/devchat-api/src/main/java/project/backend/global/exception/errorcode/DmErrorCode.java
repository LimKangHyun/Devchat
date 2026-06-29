package project.backend.global.exception.errorcode;

import lombok.AllArgsConstructor;
import lombok.Getter;
import project.common.exception.errorcode.ErrorCode;

@Getter
@AllArgsConstructor
public enum DmErrorCode implements ErrorCode {
	NOT_FOUND_DM_CHAT("DME-001", "DM채팅방을 찾을 수 없습니다. 먼저 친구추가를 해보세요", 404);

	private final String code;
	private final String message;
	private final int status;
}