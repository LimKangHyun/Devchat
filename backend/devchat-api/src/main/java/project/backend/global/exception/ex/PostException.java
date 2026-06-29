package project.backend.global.exception.ex;

import project.backend.global.exception.errorcode.PostErrorCode;
import project.common.exception.ex.BaseException;

public class PostException extends BaseException {

    public PostException(PostErrorCode errorCode) {
        super(errorCode);
    }
}