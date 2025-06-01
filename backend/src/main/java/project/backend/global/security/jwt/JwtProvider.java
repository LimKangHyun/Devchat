package project.backend.global.security.jwt;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.exceptions.SignatureVerificationException;
import com.auth0.jwt.exceptions.TokenExpiredException;
import com.auth0.jwt.interfaces.DecodedJWT;
import io.lettuce.core.RedisException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import project.backend.domain.member.dao.MemberRepository;
import project.backend.domain.member.entity.Member;
import project.backend.global.security.app.CookieUtils;
import project.backend.global.security.dto.MemberDetails;

import java.util.Date;
import java.util.Map;
import project.backend.global.redis.dao.TokenRedisRepository;
import project.backend.global.redis.entity.TokenRedis;
import project.backend.global.exception.errorcode.AuthErrorCode;
import project.backend.global.exception.ex.AuthException;


@Slf4j
@Component
@RequiredArgsConstructor
public class JwtProvider {

	public static final Long TOKEN_VALIDATION_SECOND = 10L;
	public static final Long REFRESH_TOKEN_VALIDATION_SECOND = 7 * 24 * 60 * 60L;

	private final TokenRedisRepository tokenRedisRepository;
	private final MemberRepository memberRepository;

	@Value("${jwt.info.secret}")
	private String SECRET_KEY;

	@Value("${jwt.info.issuer}")
	private String ISSUER;

	private Algorithm getSignatureAlgorithm(String secretKey) {
		return Algorithm.HMAC256(secretKey);
	}

	public Token generateTokenPair(Member member) {
		Map<String, String> payload = Map.of(
			"id", member.getId().toString(),
			"email", member.getEmail(),
			"nickname", member.getNickname()
		);

		String accessToken = generateAccessToken(payload);
		String refreshToken = generateRefreshToken(payload);

		return new Token(accessToken, refreshToken);
	}

	public Token generateTokenPair(MemberDetails memberDetails) {

		Map<String, String> payload = Map.of(
			"id", memberDetails.getId().toString(),
			"email", memberDetails.getEmail(),
			"nickname", memberDetails.getNickname()
		);

		String accessToken = generateAccessToken(payload);
		String refreshToken = generateRefreshToken(payload);

		return new Token(accessToken, refreshToken);
	}

	private String generateAccessToken(Map<String, String> payload) {
		return doGenerateToken(TOKEN_VALIDATION_SECOND, payload);
	}

	private String generateRefreshToken(Map<String, String> payload) {
		return doGenerateToken(REFRESH_TOKEN_VALIDATION_SECOND, payload);
	}

	private String regenerateAccessToken(Authentication authentication) {
		var memberDetails = (MemberDetails) authentication.getPrincipal();
		Map<String, String> payload = Map.of(
			"id", memberDetails.getId().toString(),
			"email", memberDetails.getEmail(),
			"nickname", memberDetails.getNickname()
		);

		return generateAccessToken(payload);
	}

	public String regenerateAccessToken(String refreshToken) {
		DecodedJWT decodedJWT = getJwtVerifier(REFRESH_TOKEN_VALIDATION_SECOND).verify(
			refreshToken);

		String id = decodedJWT.getClaim("id").asString();
		String email = decodedJWT.getClaim("email").asString();
		String nickname = decodedJWT.getClaim("nickname").asString();

		Map<String, String> payload = Map.of(
			"id", id,
			"email", email,
			"nickname", nickname
		);
		return generateAccessToken(payload);
	}

	public JWTVerifier getJwtVerifier(Long expiresSeconds) {
		return JWT.require(getSignatureAlgorithm(SECRET_KEY))
			.withIssuer(ISSUER)
			.acceptExpiresAt(expiresSeconds)
			.build();
	}

	public TokenStatus validateAccessToken(String token) {
		try {
			getJwtVerifier(TOKEN_VALIDATION_SECOND).verify(token);
			return TokenStatus.VALID;

		} catch (TokenExpiredException e) {
			log.warn("JWT 만료됨: {}", e.getMessage());
			return TokenStatus.EXPIRED;

		} catch (SignatureVerificationException e) {
			log.error("서명 오류: {}", e.getMessage());
			return TokenStatus.INVALID_SIGNATURE;

		} catch (JWTDecodeException e) {
			log.error("디코딩 오류: {}", e.getMessage());
			return TokenStatus.MALFORMED;

		} catch (JWTVerificationException e) {
			log.error("기타 검증 오류: {}", e.getMessage());
			return TokenStatus.UNKNOWN_ERROR;

		} catch (Exception e) {
			log.error("예상치 못한 오류: {}", e.getMessage());
			return TokenStatus.UNKNOWN_ERROR;
		}
	}


	private String doGenerateToken(Long expiration, Map<String, String> payload) {
		long now = System.currentTimeMillis();

		return JWT.create()
			.withIssuedAt(new Date(now))
			.withExpiresAt(new Date(now + expiration * 1000))
			.withPayload(payload)
			.withIssuer(ISSUER)
			.sign(getSignatureAlgorithm(SECRET_KEY));
	}

	private Long getIdFromToken(String token) {
		return Long.parseLong(getJwtVerifier(TOKEN_VALIDATION_SECOND)
			.verify(token)
			.getClaim("id")
			.asString());
	}

	public Authentication getAuthentication(String token) {

		Long id = getIdFromToken(token);
		try {
			Member member = memberRepository.findById(id)
				.orElseThrow(
					() -> new UsernameNotFoundException("존재 하지 않는 유저입니다."));

			MemberDetails memberDetails = new MemberDetails(member);

			return new UsernamePasswordAuthenticationToken(memberDetails, token,
				memberDetails.getAuthorities());
		} catch (AuthenticationException e) {
			throw e;
		}
	}

	public Authentication replaceAccessToken(HttpServletResponse response,
		String token) {
		try {

			TokenRedis tokenRedis = tokenRedisRepository.findByAccessToken(token)
				.orElseThrow(() -> new BadCredentialsException("유효하지 않은 토큰입니다."));

			String refreshToken = tokenRedis.getRefreshToken();

			//리프레쉬 토큰 유효성 검사
			JWTVerifier jwtVerifier = getJwtVerifier(REFRESH_TOKEN_VALIDATION_SECOND);
			jwtVerifier.verify(refreshToken);

			log.info("accessToken 재발급 시작 = {}", refreshToken);

			Authentication authentication = getAuthentication(refreshToken);

			// 새로운 액세스 토큰 발급
			String newAccessToken = regenerateAccessToken(authentication);

			CookieUtils.saveCookie(response, newAccessToken);

			tokenRedis.updateAccessToken(newAccessToken);

			tokenRedisRepository.save(tokenRedis);
			log.info("토큰 재발급 완료");

			return authentication;
		} catch (JwtException e) {
			log.error(e.getMessage());
			log.error("리프레시 토큰 검증 실패", e);
			throw new BadCredentialsException("세션이 만료되었습니다. 다시 로그인 해주세요.");

		} catch (RedisException e) {
			log.error(e.getMessage());
			throw new BadCredentialsException("로그인 유지에 실패했습니다. 다시 로그인해주세요.");
		} catch (AuthException e) {
			log.error(e.getMessage());
			throw e;
		}

	}
}
