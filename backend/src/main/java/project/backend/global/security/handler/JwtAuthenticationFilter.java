package project.backend.global.security.handler;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import project.backend.global.security.app.CookieUtils;
import project.backend.global.security.jwt.JwtProvider;
import project.backend.global.security.jwt.TokenStatus;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private final JwtProvider jwtProvider;

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
		FilterChain filterChain)
		throws ServletException, IOException {

		String uri = request.getRequestURI();

		if (uri.equals("/auth/refresh")) {
			filterChain.doFilter(request, response); // JWT 검사 건너뜀
			return;
		}

		Optional<Cookie> accessToken = CookieUtils.getCookie((HttpServletRequest) request,
			"accessToken");

		if (accessToken.isPresent()) {
			String token = accessToken.get().getValue();

			log.info("request.getRequestURI() = {}", request.getRequestURI());
			TokenStatus tokenStatus = jwtProvider.validateAccessToken(token);

			switch (tokenStatus) {
				case VALID -> {
					log.info("[JWT] 유효한 토큰");
					Authentication authentication = jwtProvider.getAuthentication(token);
					SecurityContextHolder.getContext().setAuthentication(authentication);
				}
				case EXPIRED -> {
					log.info("[JWT] 토큰 만료됨. 재발급 시도");
					Authentication authentication = jwtProvider.replaceAccessToken(response, token);
					if (authentication != null) {
						log.info("Successfully expired access token");
						SecurityContextHolder.getContext().setAuthentication(authentication);
					}
				}
				default -> {
					log.warn("JWT 토큰 인증 처리 불가: {}", token);
					log.warn("재로그인 필요");
					response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
					return;
				}
			}
		}

		filterChain.doFilter(request, response);
	}
}
