package project.api.global.exception.ex;

import project.api.global.exception.errorcode.ChatRoomErrorCode;
import project.common.exception.ex.BaseException;

public class ChatRoomException extends BaseException {

	public ChatRoomException(ChatRoomErrorCode errorCode) {
		super(errorCode);
	}
}