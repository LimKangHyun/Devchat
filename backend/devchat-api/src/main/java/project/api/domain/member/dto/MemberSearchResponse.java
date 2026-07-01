package project.api.domain.member.dto;

public record MemberSearchResponse(
	String username,
	String nickname,
	String status,
	boolean friend,
	boolean requestSent,
	String profileImg
) {

}
