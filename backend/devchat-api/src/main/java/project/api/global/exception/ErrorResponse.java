package project.api.global.exception;

import java.util.Map;
import lombok.Builder;
import lombok.Getter;
import org.springframework.validation.FieldError;
import project.common.exception.errorcode.ErrorCode;

@Builder
@Getter
public class ErrorResponse {

    private String code;
    private String message;
    private Map<String, Object> details;

    public static ErrorResponse toResponse(ErrorCode errorCode) {
        return ErrorResponse.builder()
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .build();
    }

    public static ErrorResponse toResponse(FieldError error) {
        return ErrorResponse.builder()
                .code("VALIDATION_FAILED")
                .message(error.getDefaultMessage())
                .build();
    }

}
