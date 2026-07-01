package project.api.global.exception.ex;

import project.api.global.exception.errorcode.CodeReviewErrorCode;
import project.common.exception.ex.BaseException;

public class CodeReviewException extends BaseException {

	public CodeReviewException(CodeReviewErrorCode errorCode) {
		super(errorCode);
	}
}