package project.api.domain.member.friend.dto;

/** 친구 목록 조회 결과. 온라인 상태는 id로 따로 채워 FriendResponse로 바꾼다 */
public record FriendRow(
	Long id,
	String username,
	String nickname,
	String profileImage
) {

	public FriendResponse toResponse(String status) {
		return new FriendResponse(username, nickname, status, profileImage);
	}
}
