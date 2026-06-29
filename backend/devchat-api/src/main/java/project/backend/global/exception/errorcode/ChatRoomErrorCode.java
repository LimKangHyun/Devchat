package project.backend.global.exception.errorcode;

import lombok.AllArgsConstructor;
import lombok.Getter;
import project.common.exception.errorcode.ErrorCode;

@Getter
@AllArgsConstructor
public enum ChatRoomErrorCode implements ErrorCode {

    CHATROOM_NOT_FOUND("CRE-001", "채팅방을 찾을 수 없습니다.", 404),
    NOT_PARTICIPANT("CRE-002", "해당 방에 참여 중인 사용자가 아닙니다.", 403),
    CHATROOM_NOT_EXIST("CRE-003", "참여 중인 채팅방이 없습니다.", 404),
    CHATROOM_CODE_NOT_FOUND("CRE-004", "존재하지 않는 초대코드입니다.", 404),
    ALREADY_PARTICIPANT("CRE-005", "이미 참여 중인 채팅방 입니다.", 409),
    PARTICIPANT_NOT_EXIST("CRE-006", "해당 채팅방에 참여 중인 사용자가 없습니다.", 404),
    OWNER_CANNOT_LEAVE("CRE-007", "방장은 채팅방에서 나갈 수 없습니다.", 403),
    OWNER_PERMISSION_REQUIRED("CRE-008", "방장 권한이 필요합니다.", 403),
    OWNER_NOT_FOUND("CRE-009", "방장을 찾을 수 없습니다.", 404),
    ASYNC_TASK_REJECTED("CRE-010", "작업량이 많아 요청이 처리되지 않았습니다. 잠시 후 다시 시도해주세요.", 429),
    ALARM_NOT_FOUND("CRE-011", "채팅방 알림 정보를 찾을 수 없습니다.", 404),
    TOO_MANY_REQUESTS("CRE-012", "요청이 너무 많습니다. 잠시 후 다시 시도해주세요.", 429),
    SERVICE_UNAVAILABLE("CRE-013", "서비스가 일시적으로 불가합니다. 잠시 후 다시 시도해주세요.", 503),
    CHATROOM_FULL("CRE-014", "채팅방 인원이 가득 찼습니다.", 409),
    CHATROOM_LIMIT_EXCEEDED("CRE-015", "참여 가능한 채팅방 수를 초과했습니다.", 409),
    TRY_AGAIN("CRE-016", "잠시 후 다시 시도해주세요.", 409),
    REPOSITORY_ALREADY_CONNECTED("CRE=017", "이미 연결된 레포지토리입니다.", 409);

    private final String code;
    private final String message;
    private final int status;
}