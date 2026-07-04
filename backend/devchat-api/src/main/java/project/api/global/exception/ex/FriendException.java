package project.api.global.exception.ex;

import project.api.global.exception.errorcode.FriendErrorCode;
import project.common.exception.ex.BaseException;

public class FriendException extends BaseException {

	public FriendException(FriendErrorCode errorCode) {
		super(errorCode);
	}
}