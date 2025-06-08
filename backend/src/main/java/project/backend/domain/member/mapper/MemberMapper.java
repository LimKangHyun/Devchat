package project.backend.domain.member.mapper;

import lombok.RequiredArgsConstructor;
import project.backend.domain.imagefile.ImageFile;
import project.backend.domain.member.dto.MemberResponse;
import project.backend.domain.member.dto.SignUpRequest;
import project.backend.domain.member.entity.Member;
import project.backend.domain.member.entity.ProviderType;
import project.backend.auth.dto.OAuthMemberDto;

@RequiredArgsConstructor
public class MemberMapper {

	public static Member toEntity(SignUpRequest request, String encryptedPassword,
		ImageFile defaultProfileImg) {
		return Member.builder()
			.username(request.getUsername())
			.email(request.getEmail())
			.password(encryptedPassword)
			.nickname(request.getNickname())
			.provider(ProviderType.LOCAL)
			.profileImage(defaultProfileImg)
			.build();
	}

	public static MemberResponse toResponse(Member member) {
		return MemberResponse.builder()
			.id(member.getId())
			.username(member.getUsername())
			.email(member.getEmail())
			.nickname(member.getNickname())
			.profileImg(member.getProfileImage().getStoreFileName())
			.build();
	}

	public static Member toEntity(OAuthMemberDto request, ImageFile defaultProfileImg) {
		return Member.builder()
			.username(request.login())
			.email(request.email())
			.nickname(request.nickname())
			.provider(ProviderType.GITHUB)
			.profileImage(defaultProfileImg)
			.build();
	}

}
