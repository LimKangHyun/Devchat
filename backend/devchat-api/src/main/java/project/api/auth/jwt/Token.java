package project.api.auth.jwt;

public record Token(
	String accessToken,
	String refreshToken
) {

}
