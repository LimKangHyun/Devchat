package project.backend.global.security.entrypoint;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import project.backend.global.exception.errorcode.ErrorCode;
import project.backend.global.exception.ex.CustomJwtException;

@Slf4j
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
		AuthenticationException authException) throws IOException {
		log.warn("인증되지 않은 사용자 접근: {} - {}", request.getRequestURI(), authException.getMessage());

		response.setContentType("application/json; charset=utf-8");

		String message = null;

		if (authException instanceof CustomJwtException jwtEx) {
			ErrorCode errorCode = jwtEx.getErrorCode();
			response.setStatus(errorCode.getStatus().value());
			message = errorCode.getMessage();
		} else {
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			message = "로그인이 필요한 서비스입니다.";
		}

		response.getWriter()
			.write("{\"message\":\"" + message + "\"}");
	}
}