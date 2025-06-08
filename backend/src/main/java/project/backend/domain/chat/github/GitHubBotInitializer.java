package project.backend.domain.chat.github;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import project.backend.domain.imagefile.ImageFile;
import project.backend.domain.imagefile.ImageFileRepository;
import project.backend.domain.imagefile.ImageType;
import project.backend.domain.member.dao.MemberRepository;
import project.backend.domain.member.entity.Member;
import project.backend.global.exception.errorcode.ImageFileErrorCode;
import project.backend.global.exception.ex.ImageFileException;

@Component
@RequiredArgsConstructor
public class GitHubBotInitializer {

	@Value("${file.images.profile.github}")
	private String githubProfile;

	@Value("${github.username}")
	private String githubUsername;

	private final ImageFileRepository imageFileRepository;
	private final MemberRepository memberRepository;

	@PostConstruct
	public void init() {

		imageFileRepository.save(ImageFile.builder()
			.storeFileName(githubProfile)
			.uploadFileName(githubProfile)
			.imageType(ImageType.PROFILE_IMAGE)
			.build());

		imageFileRepository.flush();

		Member gitHubBot = Member.builder()
			.username(githubUsername)
			.nickname(githubUsername)
			.profileImage(
				imageFileRepository.findByStoreFileName(githubProfile).orElseThrow(
					() -> new ImageFileException(ImageFileErrorCode.FILE_NOT_FOUND)
				))
			.build();

		memberRepository.save(gitHubBot);
		memberRepository.flush();
	}

}
