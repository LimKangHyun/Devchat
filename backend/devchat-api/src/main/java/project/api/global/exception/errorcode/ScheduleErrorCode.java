package project.api.global.exception.errorcode;

import lombok.AllArgsConstructor;
import lombok.Getter;
import project.common.exception.errorcode.ErrorCode;

@Getter
@AllArgsConstructor
public enum ScheduleErrorCode implements ErrorCode {
	NOT_FOUND("SCE-001", "일정을 찾을 수 없어요.", 404),
	TOO_MANY_REQUESTS("SCE-002", "요청이 너무 많아요. 잠시 후 다시 시도해 주세요.", 429),
	FORBIDDEN("SCE-003", "일정을 만든 사람만 할 수 있어요.", 403),
	INVALID_SCHEDULE("SCE-004", "일정 내용을 다시 확인해 주세요.", 400),
	INVALID_PARTICIPANT("SCE-005", "친구나 같은 채팅방에 있는 사람만 참여자로 넣을 수 있어요.", 400),
	TOO_MANY_PARTICIPANTS("SCE-006", "참여자는 50명까지 넣을 수 있어요.", 400);

	private final String code;
	private final String message;
	private final int status;
}
