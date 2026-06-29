package project.backend.global.exception.ex;

import project.backend.global.exception.errorcode.CodeReviewErrorCode;
import project.common.exception.ex.BaseException;

public class CodeReviewException extends BaseException {

	public CodeReviewException(CodeReviewErrorCode errorCode) {
		super(errorCode);
	}
}