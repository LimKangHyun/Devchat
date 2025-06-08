package project.backend.global.security.handler.form;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import project.backend.auth.app.CookieUtils;
import project.backend.auth.token.jwt.JwtProvider;
import project.backend.auth.dto.MemberDetails;
import project.backend.auth.token.jwt.Token;
import project.backend.auth.token.dao.TokenRedisRepository;
import project.backend.auth.token.entity.TokenRedis;

@Slf4j
@Component
@RequiredArgsConstructor
public class FormSuccessHandler implements AuthenticationSuccessHandler {

	private final JwtProvider jwtProvider;
	private final TokenRedisRepository tokenRedisRepository;

	@Override
	public void onAuthenticationSuccess(HttpServletRequest request,
		HttpServletResponse response,
		Authentication authentication) throws IOException {

		var memberDetails = (MemberDetails) authentication.getPrincipal();

		Token token = jwtProvider.generateTokenPair(memberDetails);

		CookieUtils.saveCookie(response, token.accessToken());

		tokenRedisRepository.save(
			new TokenRedis(memberDetails.getId(), token.accessToken(), token.refreshToken(),
				null)
		);

		response.setStatus(HttpServletResponse.SC_OK);
		response.setContentType("application/json");
		response.setCharacterEncoding("UTF-8");

		response.getWriter()
			.write("{\"message\":\"" + "로그인 성공" + "\"}");
		log.info("로그인 성공: {}", authentication.getName());
	}
}
