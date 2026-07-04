package project.api.auth.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import project.api.auth.app.AuthTokenService;
import project.api.global.util.CookieUtils;
import project.api.auth.dto.MemberDetails;

@Tag(name = "Auth", description = "인증 / 로그아웃 API")
@Slf4j
@RestController
@RequestMapping("/logout")
@RequiredArgsConstructor
public class LogoutController {

	private final AuthTokenService authTokenService;

	@Operation(summary = "로그아웃")
	@PostMapping
	public void logout(@AuthenticationPrincipal MemberDetails memberDetails,
					   HttpServletResponse response) {

		if (memberDetails != null) {
			authTokenService.logout(memberDetails.getId());
			log.info("[로그아웃] {}", memberDetails.getNickname());
		}

		CookieUtils.deleteAccessTokenCookie(response);
		CookieUtils.deleteRefreshTokenCookie(response);
		response.setStatus(HttpServletResponse.SC_NO_CONTENT);
	}

}
