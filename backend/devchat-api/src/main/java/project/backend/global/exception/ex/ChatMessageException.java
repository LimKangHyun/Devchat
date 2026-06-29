package project.backend.global.exception.ex;

import project.backend.global.exception.errorcode.ChatMessageErrorCode;
import project.common.exception.ex.BaseException;

public class ChatMessageException extends BaseException {

	public ChatMessageException(ChatMessageErrorCode errorCode) {
		super(errorCode);
	}
}