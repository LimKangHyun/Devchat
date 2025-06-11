package project.backend.global.exception.errorcode;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ChatRoomErrorCode implements ErrorCode {

    CHATROOM_NOT_FOUND("CRE-001", "채팅방을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    NOT_PARTICIPANT("CRE-002", "해당 방에 참여 중인 사용자가 아닙니다.", HttpStatus.FORBIDDEN),
    CHATROOM_NOT_EXIST("CRE-003", "참여 중인 채팅방이 없습니다.", HttpStatus.NOT_FOUND),
    CHATROOM_CODE_NOT_FOUND("CRE-004", "존재하지 않는 초대코드입니다.", HttpStatus.NOT_FOUND),
    ALREADY_PARTICIPANT("CRE-005", "이미 참여 중인 채팅방 입니다.", HttpStatus.CONFLICT),
    PARTICIPANT_NOT_EXIST("CRE-006", "해당 채팅방에 참여 중인 사용자가 없습니다.", HttpStatus.NOT_FOUND),
    OWNER_CANNOT_LEAVE("CRE-007","방장은 채팅방에서 나갈 수 없습니다.",HttpStatus.FORBIDDEN),
    OWNER_PERMISSION_REQUIRED("CRE-008","방장 권한이 필요합니다.",HttpStatus.FORBIDDEN),
    OWNER_NOT_FOUND("CRE-009","방장을 찾을 수 없습니다.",HttpStatus.NOT_FOUND);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
