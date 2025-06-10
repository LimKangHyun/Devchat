package project.backend.domain.chat.github;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import project.backend.domain.imagefile.ImageFileRepository;
import project.backend.domain.member.dao.MemberRepository;
import project.backend.domain.member.entity.Member;

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

		Member gitHubBot = Member.builder()
			.username(githubUsername)
			.nickname(githubUsername)
			.profileImage(githubProfile)
			.build();

		memberRepository.save(gitHubBot);
		memberRepository.flush();
	}

}
