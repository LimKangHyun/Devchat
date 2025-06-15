package project.backend.domain.member.dto;

public record MemberSearchResponse(
	String username,
	String nickname,
	String status,
	boolean friend,
	String profileImg
) {

}
