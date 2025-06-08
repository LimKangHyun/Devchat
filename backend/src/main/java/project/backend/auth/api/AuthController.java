package project.backend.auth.api;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import project.backend.auth.token.jwt.JwtProvider;

@Slf4j
@RestController
@RequestMapping("/token")
@RequiredArgsConstructor
public class AuthController {

	private final JwtProvider jwtProvider;

	@GetMapping("/refresh")
	public ResponseEntity<String> validateToken(@CookieValue(name = "accessToken") String token,
		HttpServletResponse response) {

		jwtProvider.replaceAccessToken(response, token);
		return ResponseEntity
			.status(HttpStatus.OK)
			.body("토큰 재발급 성공");
	}

}
