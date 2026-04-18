package project.backend.auth.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import project.backend.auth.dto.MemberDetails;
import project.backend.auth.token.jwt.JwtProvider;

@Tag(name = "Auth", description = "인증 / 토큰 API")
@Slf4j
@RestController
@RequestMapping("/token")
@RequiredArgsConstructor
public class AuthController {

	private final JwtProvider jwtProvider;

	@Operation(summary = "Access Token 재발급 (Refresh Token 기반)")
	@GetMapping("/refresh")
	public ResponseEntity<String> validateToken(@CookieValue(name = "accessToken") String token,
		HttpServletResponse response) {

		jwtProvider.replaceAccessToken(response, token);
		return ResponseEntity
			.status(HttpStatus.OK)
			.body("토큰 재발급 성공");
	}

	@Operation(summary = "WebSocket 전용 단기 토큰 발급")
	@GetMapping("/ws-token")
	public ResponseEntity<String> getWsToken(@AuthenticationPrincipal MemberDetails memberDetails) {
		String wsToken = jwtProvider.generateWsToken(memberDetails);
		return ResponseEntity.ok(wsToken);
	}
}
