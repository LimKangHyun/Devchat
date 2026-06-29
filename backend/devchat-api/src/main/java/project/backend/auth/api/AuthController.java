package project.backend.auth.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import project.backend.auth.app.AuthTokenService;

@Tag(name = "Auth", description = "인증 / 토큰 API")
@Slf4j
@RestController
@RequestMapping("/token")
@RequiredArgsConstructor
public class AuthController {

	private final AuthTokenService authTokenService;

	@Operation(summary = "Access Token 재발급 (Refresh Token 기반)")
	@PostMapping("/refresh")
	public ResponseEntity<String> refresh(
			@CookieValue(name = "refreshToken") String refreshToken,
			HttpServletResponse response) {

		authTokenService.refresh(refreshToken, response);
		return ResponseEntity.ok("토큰 재발급 성공");
	}
}