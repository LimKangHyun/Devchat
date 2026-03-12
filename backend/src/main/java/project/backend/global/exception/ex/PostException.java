package project.backend.global.exception.ex;

import project.backend.global.exception.errorcode.PostErrorCode;

public class PostException extends BaseException {

    public PostException(PostErrorCode errorCode) {
        super(errorCode);
    }
}