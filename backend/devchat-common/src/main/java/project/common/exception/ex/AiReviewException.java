package project.common.exception.ex;

import project.common.exception.errorcode.AiReviewErrorCode;

public class AiReviewException extends BaseException {
    public AiReviewException(AiReviewErrorCode errorCode) {
        super(errorCode);
    }
}