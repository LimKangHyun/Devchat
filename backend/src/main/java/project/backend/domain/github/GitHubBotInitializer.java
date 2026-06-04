package project.backend.domain.github;

import jakarta.annotation.PostConstruct;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import project.backend.domain.member.dao.MemberRepository;
import project.backend.domain.member.entity.Member;

@Component
@RequiredArgsConstructor
public class GitHubBotInitializer {

    @Value("${file.images.profile.github}")
    private String githubProfile;

    @Value("${github.username}")
    private String githubUsername;

    private final MemberRepository memberRepository;

    @PostConstruct
    public void init() {
        // username으로 Member 조회
        Optional<Member> existingBot = memberRepository.findByUsername(githubUsername);

        if (existingBot.isEmpty()) {
            Member gitHubBot = Member.builder()
                .username(githubUsername)
                .nickname(githubUsername)
                .profileImage(githubProfile)
                .build();

            memberRepository.save(gitHubBot);
            memberRepository.flush();
        }
    }

}
