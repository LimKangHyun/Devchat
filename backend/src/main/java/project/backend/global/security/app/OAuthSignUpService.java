package project.backend.global.security.app;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import project.backend.domain.imagefile.ImageFile;
import project.backend.domain.imagefile.ImageFileService;
import project.backend.domain.member.dao.MemberRepository;
import project.backend.domain.member.entity.Member;
import project.backend.domain.member.mapper.MemberMapper;
import project.backend.global.security.dto.OAuthMemberDto;

@Slf4j
@Service
@RequiredArgsConstructor
public class OAuthSignUpService {

	private final MemberRepository memberRepository;
	private final ImageFileService imageFileService;

	@Value("${file.images.profile.default}")
	private String defaultProfile;

	public Member OAuthSignUp(OAuthMemberDto request) {
		return memberRepository.findByEmail(request.email())
			.orElseGet(() -> {
				ImageFile defaultProfileImg = imageFileService.getProfileImageByStoreFileName(
					defaultProfile);
				return memberRepository.save(MemberMapper.toEntity(request, defaultProfileImg));
			});
	}
}
