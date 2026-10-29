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
import project.api.auth.app.LoginSessionService;
import project.api.global.util.CookieUtils;
import project.api.auth.dto.MemberDetails;

@Tag(name = "Auth", description = "인증 / 로그아웃 API")
@Slf4j
@RestController
@RequestMapping("/logout")
@RequiredArgsConstructor
public class LogoutController {

	private final LoginSessionService loginSessionService;
	private final AuthTokenService authTokenService;

	// 지금 기기 세션만 끊는다. 이 기기는 쿠키를 지우므로 웹소켓 알림은 보내지 않는다
	@Operation(summary = "로그아웃")
	@PostMapping
	public void logout(@AuthenticationPrincipal MemberDetails memberDetails,
					   HttpServletResponse response) {

		if (memberDetails != null) {
			if (memberDetails.getSessionId() != null) {
				loginSessionService.revokeCurrent(memberDetails.getSessionId());
			} else {
				// 옛 토큰: 저장된 리프레시 토큰을 지워 다시 못 쓰게 한다 (깃허브 토큰은 남긴다)
				authTokenService.clearLegacyRefreshToken(memberDetails.getId());
			}
			log.info("[로그아웃] {}", memberDetails.getNickname());
		}

		CookieUtils.deleteAccessTokenCookie(response);
		CookieUtils.deleteRefreshTokenCookie(response);
		response.setStatus(HttpServletResponse.SC_NO_CONTENT);
	}

}
