package project.backend.global.security.handler;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import project.backend.global.security.app.CookieUtils;
import project.backend.global.security.dto.MemberDetails;
import project.backend.global.redis.dao.TokenRedisRepository;

@Slf4j
@Component
@RequiredArgsConstructor
public class CustomLogoutSuccessHandler implements LogoutSuccessHandler {

	private final TokenRedisRepository tokenRedisRepository;

	@Override
	public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response,
		Authentication authentication) throws IOException, ServletException {

		if (authentication != null
			&& authentication.getPrincipal() instanceof MemberDetails memberDetails) {
			Long id = memberDetails.getId();
			tokenRedisRepository.deleteById(id);
			log.info("[로그아웃] {}의 리프레시 토큰 삭제", memberDetails.getNickname());
		}

		CookieUtils.deleteCookie(response);
		response.setStatus(HttpServletResponse.SC_NO_CONTENT);
	}
}
