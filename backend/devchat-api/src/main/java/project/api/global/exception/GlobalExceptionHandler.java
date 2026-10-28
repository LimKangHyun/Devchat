package project.api.global.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import project.common.exception.ex.BaseException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(BaseException.class)
	public ResponseEntity<?> handleBaseException(BaseException ex) {
		ErrorResponse response = ErrorResponse.toResponse(ex.getErrorCode());

		return ResponseEntity
			.status(ex.getStatus())
			.body(response);
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public ResponseEntity<?> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
		return ResponseEntity
			.status(HttpStatus.PAYLOAD_TOO_LARGE)
			.body(ErrorResponse.builder()
				.code("FILE_TOO_LARGE")
				.message("이미지 용량이 너무 큽니다. 10MB 이하로 올려주세요.")
				.build());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<?> handleMethodArgumentNotValidException(
		MethodArgumentNotValidException ex) {
		log.info("ex.getMessage() = {}", ex.getMessage());
		//필드 에러
		if (!ex.getBindingResult().getFieldErrors().isEmpty()) {
			FieldError fieldError = ex.getBindingResult().getFieldErrors().getFirst();
			return ResponseEntity
				.status(HttpStatus.BAD_REQUEST)
				.body(ErrorResponse.toResponse(fieldError));
		}

		//객체 에러(@AssertTrue 같은거)
		if (!ex.getBindingResult().getGlobalErrors().isEmpty()) {
			String message = ex.getBindingResult().getGlobalErrors().getFirst().getDefaultMessage();
			return ResponseEntity
				.status(HttpStatus.BAD_REQUEST)
				.body(ErrorResponse.builder()
					.code("VALIDATION_FAILED")
					.message(message)
					.build());
		}

		return ResponseEntity
			.status(HttpStatus.BAD_REQUEST)
			.body(ErrorResponse.builder()
				.code("UNEXPECTED_VALIDATION_FAILED")
				.message(ex.getMessage())
				.build());
	}
}