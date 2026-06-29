package project.common.exception.ex;

import project.common.exception.errorcode.ErrorCode;
import project.common.exception.errorcode.IndexingErrorCode;

public class IndexingException extends BaseException {
    public IndexingException(IndexingErrorCode errorCode) {
        super(errorCode);
    }

    public ErrorCode getErrorCode() {
        return super.getErrorCode();
    }
}