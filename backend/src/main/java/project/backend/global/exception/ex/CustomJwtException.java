package project.backend.global.exception.ex;

import lombok.Getter;
import org.springframework.security.core.AuthenticationException;
import project.backend.global.exception.errorcode.TokenErrorCode;

@Getter
public class CustomJwtException extends AuthenticationException {

	private final TokenErrorCode errorCode;

	public CustomJwtException(TokenErrorCode errorCode) {
		super(errorCode.getMessage());
		this.errorCode = errorCode;
	}

	public CustomJwtException(TokenErrorCode errorCode, Throwable cause) {
		super(errorCode.getMessage(), cause);
		this.errorCode = errorCode;
	}
}
