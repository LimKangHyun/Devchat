package project.api.global.exception.ex;

import project.common.exception.errorcode.ErrorCode;
import project.common.exception.ex.BaseException;

public class FeedbackException extends BaseException {

	public FeedbackException(ErrorCode errorCode) {
		super(errorCode);
	}

	public FeedbackException(ErrorCode errorCode, Throwable cause) {
		super(errorCode, cause);
	}
}
