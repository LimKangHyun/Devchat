package project.backend.global.exception.ex;

import lombok.Getter;
import project.backend.global.exception.errorcode.AuthErrorCode;

@Getter
public class AuthException extends BaseException {

	public AuthException(AuthErrorCode errorCode) {
		super(errorCode);
	}

}

