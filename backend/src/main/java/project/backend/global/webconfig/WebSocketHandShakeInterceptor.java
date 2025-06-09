package project.backend.global.webconfig;

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
			Cookie[] cookies = httpServletRequest.getCookies();

			if (cookies != null) {
				for (Cookie cookie : cookies) {
					if ("accessToken".equals(cookie.getName())) {
						String token = cookie.getValue();

						if (jwtProvider.validateAccessToken(token) == TokenStatus.VALID) {
							Authentication authentication = jwtProvider.getAuthentication(token);

							// ✅ SecurityContext에 인증 객체 저장
							SecurityContextHolder.getContext().setAuthentication(authentication);

							// 🔁 이후 ChannelInterceptor에서 꺼내쓰기 위해 attributes에도 저장
							attributes.put("auth", authentication);
						}
						break;
					}
				}
			} else {
				log.error("웹소켓 핸드쉐이크 요청 시 쿠키에 accessToken이 포함되지 않음");
				return false;
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
