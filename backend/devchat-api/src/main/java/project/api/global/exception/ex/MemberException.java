package project.api.global.exception.ex;

import project.api.global.exception.errorcode.MemberErrorCode;
import project.common.exception.ex.BaseException;

public class MemberException extends BaseException {

    public MemberException(MemberErrorCode errorCode) {
        super(errorCode);
    }
}