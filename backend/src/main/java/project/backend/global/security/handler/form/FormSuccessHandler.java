package project.backend.global.security.handler.form;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import project.backend.auth.app.AuthTokenService;
import project.backend.global.util.CookieUtils;
import project.backend.auth.dto.MemberDetails;
import project.backend.auth.jwt.JwtProvider;
import project.backend.auth.jwt.Token;
import project.backend.domain.member.app.ProfileImageCache;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class FormSuccessHandler implements AuthenticationSuccessHandler {

	private final JwtProvider jwtProvider;
	private final AuthTokenService authTokenService;
	private final ProfileImageCache profileImageCache;

	@Override
	public void onAuthenticationSuccess(HttpServletRequest request,
										HttpServletResponse response,
										Authentication authentication) throws IOException {

		MemberDetails memberDetails = (MemberDetails) authentication.getPrincipal();

		Token token = jwtProvider.generateTokenPair(memberDetails);

		CookieUtils.saveAccessTokenCookie(response, token.accessToken());

		CookieUtils.saveRefreshTokenCookie(response, token.refreshToken());

		authTokenService.saveToken(memberDetails.getId(), token.refreshToken(), null);

		profileImageCache.setProfileImage(memberDetails.getId(), memberDetails.getProfileImg());

		response.setStatus(HttpServletResponse.SC_OK);
		response.setContentType("application/json");
		response.setCharacterEncoding("UTF-8");
		response.getWriter().write("{\"message\":\"로그인 성공\"}");

		log.info("로그인 성공: {}", authentication.getName());
	}
}