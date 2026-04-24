package project.backend.auth.jwt;

public record Token(
	String accessToken,
	String refreshToken
) {

}
