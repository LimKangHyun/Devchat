package project.api.global.exception.errorcode;

import lombok.AllArgsConstructor;
import lombok.Getter;
import project.common.exception.errorcode.ErrorCode;

@Getter
@AllArgsConstructor
public enum PostErrorCode implements ErrorCode {

    POST_NOT_FOUND("POE-001", "게시글을 찾을 수 없습니다.", 404),
    POST_FORBIDDEN("POE-002", "게시글 수정/삭제 권한이 없습니다.", 403),
    POST_ALREADY_CLOSED("POE-003", "이미 마감된 모집글입니다.", 400),
    POST_ALREADY_FULL("POE-004", "모집 인원이 꽉 찼습니다.", 400),
    POST_MAX_COUNT_INVALID("POE-005", "모집 인원을 현재 참여 인원보다 작게 설정할 수 없습니다.", 400),
    ALREADY_APPLIED("POE-006", "이미 신청한 모집글입니다.", 409),
    APPLICANT_NOT_FOUND("POE-007", "신청자를 찾을 수 없습니다.", 404),
    CHATROOM_ALREADY_HAS_POST("P0E-009", "이미 게시글이 연결된 채팅방입니다.", 409),
    CANNOT_APPLY_OWN_POST("POE-008", "본인이 작성한 모집글에는 신청할 수 없습니다.", 400),
    APPLY_TOO_SOON("POE-009", "거절 후 24시간이 지나야 재신청할 수 있습니다.", 429);

    private final String code;
    private final String message;
    private final int status;

}