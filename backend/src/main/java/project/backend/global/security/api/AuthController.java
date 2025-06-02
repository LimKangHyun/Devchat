package project.backend.global.security.api;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import project.backend.global.security.jwt.JwtProvider;

@Slf4j
@RestController
@RequestMapping("/token")
@RequiredArgsConstructor
public class AuthController {

	private final JwtProvider jwtProvider;

	@GetMapping("/sync")
	public ResponseEntity<String> validateToken(Authentication authentication,
		HttpServletResponse response) {

		jwtProvider.getAccessTokenByAuthentication(authentication, response);
		return ResponseEntity
			.status(HttpStatus.OK)
			.body("토큰 동기화 성공");
	}

}
