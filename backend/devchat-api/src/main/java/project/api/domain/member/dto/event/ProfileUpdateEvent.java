package project.api.domain.member.dto.event;

import project.api.domain.member.entity.Member;

public record ProfileUpdateEvent(
	Long userId,
	String nickname,
	String profileImageUrl
) {

	public static ProfileUpdateEvent of(Member member) {
		return new ProfileUpdateEvent(
			member.getId(),
			member.getNickname(),
			member.getProfileImage()
		);
	}

}
