package project.api.auth.dto;

public record OAuthMemberDto(
	String email,
	String nickname,
	String login
) {

}
