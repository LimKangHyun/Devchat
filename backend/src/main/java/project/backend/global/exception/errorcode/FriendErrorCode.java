package project.backend.global.exception.errorcode;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum FriendErrorCode implements ErrorCode {
	PENDING_FRIEND_REQUEST("FRE-001", "이미 요청 중 입니다.", HttpStatus.CONFLICT),
	ALREADY_FRIEND("FRE-002", "이미 친구 입니다.", HttpStatus.BAD_REQUEST),
	NOT_FOUNT_FRIEND_REQUEST("FRE-003", "친구요청을 찾을 수 없습니다.", HttpStatus.NOT_FOUND);

	private final String code;
	private final String message;
	private final HttpStatus status;


}
