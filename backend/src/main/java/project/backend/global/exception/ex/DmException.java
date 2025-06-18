package project.backend.global.exception.ex;

import project.backend.global.exception.errorcode.ErrorCode;

public class DmException extends BaseException {

	public DmException(ErrorCode errorCode) {
		super(errorCode);
	}
}
