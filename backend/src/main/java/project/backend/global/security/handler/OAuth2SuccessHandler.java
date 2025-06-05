package project.backend.global.security.handler;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;
import project.backend.domain.member.dao.MemberRepository;
import project.backend.domain.member.entity.Member;
import project.backend.global.security.app.CookieUtils;
import project.backend.global.security.app.OAuthSignUpService;
import project.backend.global.security.dto.OAuthMemberDto;
import project.backend.global.security.jwt.JwtProvider;
import project.backend.global.security.jwt.Token;
import project.backend.global.redis.entity.TokenRedis;
import project.backend.global.redis.dao.TokenRedisRepository;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

	@Value("${jwt.redirection.base}")
	private String baseUrl;

	private final JwtProvider jwtProvider;
	private final OAuthSignUpService oAuthSignUpService;
	private final MemberRepository memberRepository;
	private final TokenRedisRepository tokenRedisRepository;

	@Override
	@Transactional
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
		Authentication authentication) throws IOException, ServletException {

		var oAuth2User = (OAuth2User) authentication.getPrincipal();

		log.info("oAuth2User = {}", oAuth2User);

		OAuthMemberDto userDto = new OAuthMemberDto(
			(String) oAuth2User.getAttributes().get("email"),
			(String) oAuth2User.getAttributes().get("name"),
			(String) oAuth2User.getAttributes().get("login"));

		// 기존에 없는 email이면 회원가입
		Member member = oAuthSignUpService.OAuthSignUp(userDto);

		Token token = jwtProvider.generateTokenPair(member);

		//쿠키 생성 및 저장
		CookieUtils.saveCookie(response, token.accessToken());

		// 깃허브 엑세스 토큰
		var githubAccess = (String) oAuth2User.getAttributes().get("githubAccess");

		tokenRedisRepository.save(
			new TokenRedis(member.getId(), token.accessToken(), token.refreshToken(),
				githubAccess));

		log.info("OAuth 로그인 성공: {}", member.getEmail());

		String redirectUrl = UriComponentsBuilder.fromUriString(baseUrl)
			.build().toUriString();

		log.info("OAuth 로그인 후 리다이렉트 URL = {}", redirectUrl);
		response.setStatus(HttpServletResponse.SC_OK);
		response.setContentType("application/json");
		response.sendRedirect(redirectUrl);

	}


}
