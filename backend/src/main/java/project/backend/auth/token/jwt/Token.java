package project.backend.auth.token.jwt;

public record Token(
	String accessToken,
	String refreshToken
) {

}
