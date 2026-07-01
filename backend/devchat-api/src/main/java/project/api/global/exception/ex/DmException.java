package project.api.global.exception.ex;

import project.common.exception.errorcode.ErrorCode;
import project.common.exception.ex.BaseException;

public class DmException extends BaseException {

	public DmException(ErrorCode errorCode) {
		super(errorCode);
	}
}