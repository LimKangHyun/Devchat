package project.backend.global.security.interceptor;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Optional;
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
			Optional<Cookie> accessTokenCookie = CookieUtils.getCookie(httpServletRequest,
				"accessToken");

			if (accessTokenCookie.isPresent()) {
				String token = accessTokenCookie.get().getValue();
				Authentication authentication = jwtProvider.getAuthentication(token);

				// 이후 ChannelInterceptor에서 꺼내쓰기 위해 attributes에 저장
				attributes.put("auth", authentication);
			} else {
				log.error("웹소켓 핸드쉐이크 요청 시 쿠키에 accessToken이 포함되지 않음");
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
