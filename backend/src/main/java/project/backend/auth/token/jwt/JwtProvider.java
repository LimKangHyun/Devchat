package project.backend.auth.token.jwt;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.exceptions.SignatureVerificationException;
import com.auth0.jwt.exceptions.TokenExpiredException;
import com.auth0.jwt.interfaces.DecodedJWT;
import io.lettuce.core.RedisException;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Date;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import project.backend.domain.member.dao.MemberRepository;
import project.backend.domain.member.entity.Member;
import project.backend.global.exception.errorcode.TokenErrorCode;
import project.backend.global.exception.ex.CustomJwtException;
import project.backend.auth.token.dao.TokenRedisRepository;
import project.backend.auth.token.entity.TokenRedis;
import project.backend.auth.app.CookieUtils;
import project.backend.auth.dto.MemberDetails;


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

	public Token generateTokenPair(MemberDetails memberDetails) {

		Map<String, String> payload = Map.of(
			"username", memberDetails.getUsername()
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

	public String regenerateAccessToken(String refreshToken) {
		DecodedJWT decodedJWT = getJwtVerifier(REFRESH_TOKEN_VALIDATION_SECOND).verify(
			refreshToken);

		String username = decodedJWT.getClaim("username").asString();

		Map<String, String> payload = Map.of(
			"username", username
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

	private String getUsernameFromToken(String token) {
		return getJwtVerifier(TOKEN_VALIDATION_SECOND)
			.verify(token)
			.getClaim("username")
			.asString();
	}

	public Authentication getAuthentication(String token) {

		String username = getUsernameFromToken(token);
		Member member = memberRepository.findByUsername(username)
			.orElseThrow(() -> new UsernameNotFoundException("존재 하지 않는 유저입니다."));

		MemberDetails memberDetails = new MemberDetails(member);

		return new UsernamePasswordAuthenticationToken(memberDetails, token,
			memberDetails.getAuthorities());

	}

	@Transactional
	public void replaceAccessToken(HttpServletResponse response,
		String token) {
		try {
			TokenRedis tokenRedis = tokenRedisRepository.findByAccessToken(token)
				.orElseThrow(() -> new CustomJwtException(TokenErrorCode.NOT_FOUND_TOKEN));

			String refreshToken = tokenRedis.getRefreshToken();

			//리프레쉬 토큰 유효성 검사
			JWTVerifier jwtVerifier = getJwtVerifier(REFRESH_TOKEN_VALIDATION_SECOND);
			jwtVerifier.verify(refreshToken);

			log.info("accessToken 재발급 시작 = {}", token);

			// 새로운 액세스 토큰 발급
			String newAccessToken = regenerateAccessToken(refreshToken);

			CookieUtils.saveCookie(response, newAccessToken);

			tokenRedis.updateAccessToken(newAccessToken);

			tokenRedisRepository.save(tokenRedis);

		} catch (TokenExpiredException e) {
			log.error("리프레시 토큰 만료");
			throw new CustomJwtException(TokenErrorCode.EXPIRED_TOKEN);

		} catch (JwtException e) {
			log.error("리프레시 토큰 검증 실패", e);
			throw new CustomJwtException(TokenErrorCode.INVALID_TOKEN);

		} catch (RedisException e) {
			log.error(e.getMessage());
			throw new CustomJwtException(TokenErrorCode.UNKNOWN_ERROR);
		}

	}
}
