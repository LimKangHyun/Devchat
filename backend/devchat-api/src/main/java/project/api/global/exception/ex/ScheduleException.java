package project.api.global.exception.ex;

import project.common.exception.errorcode.ErrorCode;
import project.common.exception.ex.BaseException;

public class ScheduleException extends BaseException {

	public ScheduleException(ErrorCode errorCode) {
		super(errorCode);
	}

	public ScheduleException(ErrorCode errorCode, Throwable cause) {
		super(errorCode, cause);
	}
}
