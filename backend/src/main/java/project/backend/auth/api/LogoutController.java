package project.backend.auth.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import project.backend.auth.app.CookieUtils;
import project.backend.auth.dto.MemberDetails;
import project.backend.auth.token.dao.TokenRedisRepository;

@Tag(name = "Auth", description = "인증 / 로그아웃 API")
@Slf4j
@RestController
@RequestMapping("/logout")
@RequiredArgsConstructor
public class LogoutController {

	private final TokenRedisRepository tokenRedisRepository;

	@Operation(summary = "로그아웃")
	@PostMapping
	public void logout(@AuthenticationPrincipal MemberDetails memberDetails,
		HttpServletResponse response) {

		SecurityContextHolder.clearContext();
		CookieUtils.deleteCookie(response);
		tokenRedisRepository.deleteById(memberDetails.getId());
		log.info("[로그아웃] {}", memberDetails.getNickname());
		response.setStatus(HttpServletResponse.SC_NO_CONTENT);

	}

}
