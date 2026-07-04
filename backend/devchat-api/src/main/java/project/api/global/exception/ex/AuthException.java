package project.api.global.exception.ex;

import lombok.Getter;
import project.api.global.exception.errorcode.AuthErrorCode;
import project.common.exception.ex.BaseException;

@Getter
public class AuthException extends BaseException {

	public AuthException(AuthErrorCode errorCode) {
		super(errorCode);
	}

}