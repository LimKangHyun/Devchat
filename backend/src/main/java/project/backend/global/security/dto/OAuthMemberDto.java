package project.backend.global.security.dto;

public record OAuthMemberDto(
	String email,
	String nickname,
	String login
) {

}
