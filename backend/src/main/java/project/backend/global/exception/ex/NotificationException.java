package project.backend.global.exception.ex;

import project.backend.global.exception.errorcode.ErrorCode;

public class NotificationException extends BaseException {

	public NotificationException(ErrorCode errorCode) {
		super(errorCode);
	}
}
