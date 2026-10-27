package project.api.domain.member.dto;

public record MemberSearchResponse(
	String username,
	String nickname,
	boolean friend,
	boolean requestSent,
	String profileImg
) {

}
