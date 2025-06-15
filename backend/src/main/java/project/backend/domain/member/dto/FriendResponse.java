package project.backend.domain.member.dto;

public record FriendResponse(
	String username,
	String nickname,
	String status,
	String profileImg
) {

}
