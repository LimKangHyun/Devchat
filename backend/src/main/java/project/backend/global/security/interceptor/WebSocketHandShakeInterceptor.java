package project.backend.global.security.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import project.backend.auth.token.jwt.JwtProvider;
import project.backend.auth.token.jwt.TokenStatus;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketHandShakeInterceptor implements HandshakeInterceptor {

	private final JwtProvider jwtProvider;

	@Override
	public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
		WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {

		if (request instanceof ServletServerHttpRequest servletRequest) {
			HttpServletRequest httpServletRequest = servletRequest.getServletRequest();

			String token = httpServletRequest.getParameter("token");

			if (token == null || token.isBlank()) {
				log.warn("[WS] 토큰 없음 - 핸드셰이크 거부");
				return false;
			}

			TokenStatus tokenStatus = jwtProvider.validateWsToken(token);

			if (tokenStatus == TokenStatus.VALID) {
				log.info("[WS] 유효한 WS 토큰 - 핸드셰이크 허용");
				Authentication authentication = jwtProvider.getAuthenticationFromWsToken(token);
				attributes.put("auth", authentication);
			} else {
				log.warn("[WS] 유효하지 않은 WS 토큰 - 핸드셰이크 거부");
				return false;
			}
		}
		return true;
	}

	@Override
	public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
		WebSocketHandler wsHandler, Exception exception) {
		SecurityContextHolder.clearContext();
	}
}