package project.backend.domain.member.dto.event;

public record ProfileUpdateEvent(
	Long userId,
	String nickname,
	String profileImageUrl
) {

}
