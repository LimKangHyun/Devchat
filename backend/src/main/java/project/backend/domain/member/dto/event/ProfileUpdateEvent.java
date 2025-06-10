package project.backend.domain.member.dto.event;

import java.util.Optional;
import project.backend.domain.imagefile.ImageFile;
import project.backend.domain.member.entity.Member;

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
