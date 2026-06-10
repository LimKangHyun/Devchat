package project.backend.global.exception.ex;

import project.backend.global.exception.errorcode.AiReviewErrorCode;

public class AiReviewException extends BaseException {
    public AiReviewException(AiReviewErrorCode errorCode) {
        super(errorCode);
    }
}
