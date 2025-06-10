package project.backend.global.exception;


import java.util.HashMap;
import java.util.Map;

import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import project.backend.global.exception.ex.AuthException;
import project.backend.global.exception.ex.BaseException;

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
