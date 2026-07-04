package project.api.global.exception.ex;

import project.common.exception.errorcode.ErrorCode;
import project.common.exception.ex.BaseException;

public class GitHubException extends BaseException {

    public GitHubException(ErrorCode errorCode) {
        super(errorCode);
    }
}