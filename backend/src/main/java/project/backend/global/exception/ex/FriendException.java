package project.backend.global.exception.ex;

import project.backend.global.exception.errorcode.FriendErrorCode;

public class FriendException extends BaseException {

	public FriendException(FriendErrorCode errorCode) {
		super(errorCode);
	}
}
