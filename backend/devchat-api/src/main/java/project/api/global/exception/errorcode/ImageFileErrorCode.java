package project.api.global.exception.errorcode;

import lombok.AllArgsConstructor;
import lombok.Getter;
import project.common.exception.errorcode.ErrorCode;

@Getter
@AllArgsConstructor
public enum ImageFileErrorCode implements ErrorCode {
    FILE_SAVE_FAILURE("IE-001", "이미지 저장 실패", 500),
    FILE_NOT_FOUND("IE-002", "이미지를 찾을 수 없습니다.", 404),
    INVALID_IMAGE_TYPE("IE-003", "적합하지 않은 파일입니다.", 400),
    INVALID_ROUTE("IE-004", "유효하지 않은 경로입니다.", 400);

    private final String code;
    private final String message;
    private final int status;

}