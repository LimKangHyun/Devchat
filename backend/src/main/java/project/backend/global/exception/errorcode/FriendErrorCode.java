package project.backend.global.exception.errorcode;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum FriendErrorCode implements ErrorCode {
	ALREADY_REQUESTED_FRIEND("FRE-001", "이미 요청 중 이거나, 이미 친구입니다.", HttpStatus.CONFLICT),
	;

	private final String code;
	private final String message;
	private final HttpStatus status;


}
