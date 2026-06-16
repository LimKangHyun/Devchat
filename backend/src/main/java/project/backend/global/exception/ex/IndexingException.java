package project.backend.global.exception.ex;

import project.backend.global.exception.errorcode.IndexingErrorCode;

public class IndexingException extends BaseException {
    public IndexingException(IndexingErrorCode errorCode) {
        super(errorCode);
    }
}