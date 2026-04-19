package project.backend.global.security.interceptor;

import jakarta.servlet.http.Cookie;
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
import project.backend.auth.app.CookieUtils;
import project.backend.auth.token.jwt.JwtProvider;
import project.backend.auth.token.jwt.TokenStatus;
import project.backend.global.exception.errorcode.TokenErrorCode;
import project.backend.global.exception.ex.CustomJwtException;

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

			Cookie cookie = CookieUtils.getCookie(httpServletRequest,
					"accessToken")
				.orElseThrow(() -> new CustomJwtException(TokenErrorCode.NOT_FOUND_TOKEN));
			String accessToken = cookie.getValue();

			// 토큰 검증
			TokenStatus tokenStatus = jwtProvider.validateAccessToken(accessToken);

			if (tokenStatus == TokenStatus.VALID) {
				log.info("[JWT] 유효한 토큰");
				Authentication authentication = jwtProvider.getAuthentication(accessToken);
				// 이후 ChannelInterceptor에서 꺼내쓰기 위해 attributes에 저장
				attributes.put("auth", authentication);

			} else {
				return false; //handshake 허용x
			}

		}
		return true; // handshake 허용
	}

	@Override
	public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
		WebSocketHandler wsHandler, Exception exception) {
		SecurityContextHolder.clearContext(); //cleanup
	}
}