package project.backend.auth.app;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

public class CookieUtils {

	private static final String COOKIE_NAME = "accessToken";

	public static void saveCookie(HttpServletResponse response, String accessToken) {
		ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, accessToken)
			.httpOnly(true)
			.secure(true)
			.sameSite("None")
			.path("/")
			.maxAge(Duration.ofDays(7))
			.build();
		response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
	}

	public static void deleteCookie(HttpServletResponse response) {
		ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, "")
			.httpOnly(true)
			.secure(true)
			.sameSite("None")
			.path("/")
			.maxAge(Duration.ZERO)
			.build();
		response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
	}

	public static Optional<Cookie> getCookie(HttpServletRequest request, String name) {
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return Optional.empty();
		}

		return Arrays.stream(cookies)
			.filter(c -> c.getName().equals(name))
			.findFirst();
	}

}
