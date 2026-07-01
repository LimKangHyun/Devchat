package project.api.global.exception.ex;

import project.common.exception.errorcode.ErrorCode;
import project.common.exception.ex.BaseException;

public class NotificationException extends BaseException {

	public NotificationException(ErrorCode errorCode) {
		super(errorCode);
	}
}