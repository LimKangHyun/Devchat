package project.api.global.exception.ex;

import project.common.exception.errorcode.ErrorCode;
import project.common.exception.ex.BaseException;

public class LoginSessionException extends BaseException {

	public LoginSessionException(ErrorCode errorCode) {
		super(errorCode);
	}
}
