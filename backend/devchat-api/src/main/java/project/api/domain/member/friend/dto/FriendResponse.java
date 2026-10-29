package project.api.domain.member.friend.dto;

public record FriendResponse(
	Long id,
	String username,
	String nickname,
	String status,
	String profileImage
) {

}
