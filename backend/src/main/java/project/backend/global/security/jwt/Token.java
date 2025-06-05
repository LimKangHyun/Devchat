package project.backend.global.security.jwt;

public record Token(
	String accessToken,
	String refreshToken
) {

}
