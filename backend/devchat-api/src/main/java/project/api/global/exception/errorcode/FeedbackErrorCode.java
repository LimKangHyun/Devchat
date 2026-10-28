package project.api.global.exception.errorcode;

import lombok.AllArgsConstructor;
import lombok.Getter;
import project.common.exception.errorcode.ErrorCode;

@Getter
@AllArgsConstructor
public enum FeedbackErrorCode implements ErrorCode {
	FEEDBACK_NOT_CONFIGURED("FB-001", "지금은 피드백을 받을 수 없어요. 잠시 후 다시 시도해 주세요.", 503),
	TOO_MANY_FEEDBACK("FB-002", "피드백은 1시간에 5번까지 보낼 수 있어요.", 429),
	FEEDBACK_SEND_FAILED("FB-003", "피드백을 보내지 못했어요. 잠시 후 다시 시도해 주세요.", 502);

	private final String code;
	private final String message;
	private final int status;
}
