package project.backend.global.security.handler;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Enumeration;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2FailureHandler extends SimpleUrlAuthenticationFailureHandler {

	@Override
	public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
		AuthenticationException exception) throws IOException, ServletException {
		log.error("OAuth 로그인 실패: {}", exception.getMessage(), exception);
		log.info("요청 URI: {}", request.getRequestURI());
		log.info("요청 전체 URL: {}", request.getRequestURL());
		log.info("Query String: {}", request.getQueryString());

		Enumeration<String> paramNames = request.getParameterNames();
		while (paramNames.hasMoreElements()) {
			String name = paramNames.nextElement();
			log.info("요청 파라미터: {} = {}", name, request.getParameter(name));
		}
		response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "OAuth 로그인 실패");
	}
}
