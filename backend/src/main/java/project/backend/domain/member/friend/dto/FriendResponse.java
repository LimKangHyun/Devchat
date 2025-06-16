package project.backend.domain.member.friend.dto;

public record FriendResponse(
	String username,
	String nickname,
	String status,
	String profileImg
) {

}
