package project.api.global.exception.errorcode;

import lombok.AllArgsConstructor;
import lombok.Getter;
import project.common.exception.errorcode.ErrorCode;

@Getter
@AllArgsConstructor
public enum NotificationErrorCode implements ErrorCode {
	NOT_FOUND_NOTIFICATION("NE-001", "해당 알림을 찾을 수 없습니다.", 404);

	private final String code;
	private final String message;
	private final int status;

}