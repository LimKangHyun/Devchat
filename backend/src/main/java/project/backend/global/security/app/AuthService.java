package project.backend.global.security.app;


import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import project.backend.global.redis.dao.TokenRedisRepository;
import project.backend.global.security.jwt.JwtProvider;
import project.backend.global.security.jwt.TokenStatus;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

	private final JwtProvider jwtProvider;
	private final TokenRedisRepository tokenRedisRepository;

	public void validateAuthentication(HttpServletRequest request, HttpServletResponse response) {

		String accessToken = getAccessTokenFromCookie(request);

		if (accessToken == null) {
			throw new BadCredentialsException("로그인 유지에 실패했습니다. 다시 로그인해주세요.");
		}

		TokenStatus tokenStatus = jwtProvider.validateAccessToken(accessToken);
		log.info("tokenStatus = {}", tokenStatus);

		switch (tokenStatus) {
			case VALID:
				response.setStatus(HttpServletResponse.SC_OK);
				break;

			case EXPIRED:
				jwtProvider.replaceAccessToken(response, accessToken);
				break;

			default:
				throw new BadCredentialsException("다시 로그인해주세요.");
		}

	}


	private String getAccessTokenFromCookie(HttpServletRequest request) {
		Cookie[] cookies = request.getCookies();
		if (cookies != null) {
			for (Cookie cookie : cookies) {
				if ("accessToken".equals(cookie.getName())) {
					return cookie.getValue();
				}
			}
		}
		return null;
	}


}
