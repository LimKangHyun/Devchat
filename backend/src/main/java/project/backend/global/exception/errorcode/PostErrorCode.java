package project.backend.global.exception.errorcode;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum PostErrorCode implements ErrorCode {

    POST_NOT_FOUND("POE-001", "게시글을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    POST_FORBIDDEN("POE-002", "게시글 수정/삭제 권한이 없습니다.", HttpStatus.FORBIDDEN),
    POST_ALREADY_CLOSED("POE-003", "이미 마감된 모집글입니다.", HttpStatus.BAD_REQUEST),
    POST_ALREADY_FULL("POE-004", "모집 인원이 꽉 찼습니다.", HttpStatus.BAD_REQUEST),
    POST_MAX_COUNT_INVALID("POE-005", "모집 인원을 현재 참여 인원보다 작게 설정할 수 없습니다.", HttpStatus.BAD_REQUEST),
    ALREADY_APPLIED("POE-006", "이미 신청한 모집글입니다.", HttpStatus.CONFLICT),
    APPLICANT_NOT_FOUND("POE-007", "신청자를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    CHATROOM_ALREADY_HAS_POST("P0E-009", "이미 게시글이 연결된 채팅방입니다.", HttpStatus.CONFLICT),
    CANNOT_APPLY_OWN_POST("POE-008", "본인이 작성한 모집글에는 신청할 수 없습니다.", HttpStatus.BAD_REQUEST),
    APPLY_TOO_SOON("POE-009", "거절 후 24시간이 지나야 재신청할 수 있습니다.", HttpStatus.TOO_MANY_REQUESTS);

    private final String code;
    private final String message;
    private final HttpStatus status;
}