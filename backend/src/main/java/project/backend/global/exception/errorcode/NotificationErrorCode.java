package project.backend.global.exception.errorcode;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum NotificationErrorCode implements ErrorCode {
	NOT_FOUND_NOTIFICATION("NE-001", "해당 알림을 찾을 수 없습니다.", HttpStatus.NOT_FOUND);


	private final String code;
	private final String message;
	private final HttpStatus status;

}
