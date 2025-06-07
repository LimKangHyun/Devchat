package project.backend.global.security.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.MessagingException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import project.backend.global.security.app.CookieUtils;
import project.backend.global.security.jwt.JwtProvider;
import project.backend.global.security.dto.MemberDetails;
import project.backend.global.security.jwt.Token;
import project.backend.global.redis.dao.TokenRedisRepository;
import project.backend.global.redis.entity.TokenRedis;

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
