package project.backend.global.exception.ex;

import lombok.Getter;
import project.backend.global.exception.errorcode.TokenErrorCode;
import project.common.exception.ex.BaseException;

@Getter
public class CustomJwtException extends BaseException {

	public CustomJwtException(TokenErrorCode errorCode) {
		super(errorCode);
	}

	public CustomJwtException(TokenErrorCode errorCode, Throwable cause) {
		super(errorCode);
		initCause(cause);
	}
}