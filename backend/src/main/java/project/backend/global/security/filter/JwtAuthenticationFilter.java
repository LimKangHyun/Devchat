package project.backend.global.security.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import project.backend.global.exception.errorcode.TokenErrorCode;
import project.backend.global.exception.ex.CustomJwtException;
import project.backend.auth.app.CookieUtils;
import project.backend.auth.token.jwt.JwtProvider;
import project.backend.auth.token.jwt.TokenStatus;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private final JwtProvider jwtProvider;

	private static final List<String> WHITE_LIST = List.of(
		"/signup",
		"/login",
		"/token/refresh",
		"/health"
	);

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
		FilterChain filterChain)
		throws ServletException, IOException {

		String requestURI = request.getRequestURI();
		log.debug("[JWT Filter] 요청 URI: {}", requestURI);
		if (requestURI.startsWith("/github/") || WHITE_LIST.contains(request.getRequestURI())) {
			filterChain.doFilter(request, response); // JWT 검사 건너뜀
			return;
		}

		log.info("JWT필터 도달 = {}", request.getRequestURI());

		Cookie cookie = CookieUtils.getCookie(request,
				"accessToken")
			.orElseThrow(() -> new CustomJwtException(TokenErrorCode.NOT_FOUND_TOKEN));
		String accessToken = cookie.getValue();

		TokenStatus tokenStatus = jwtProvider.validateAccessToken(accessToken);

		if (tokenStatus == TokenStatus.VALID) {
			log.info("[JWT] 유효한 토큰");
			Authentication authentication = jwtProvider.getAuthentication(accessToken);
			SecurityContextHolder.getContext().setAuthentication(authentication);

		} else {
			SecurityContextHolder.clearContext();
		}

		filterChain.doFilter(request, response);
	}
}
