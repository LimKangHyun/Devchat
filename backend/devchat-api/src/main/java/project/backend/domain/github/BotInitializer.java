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
public class BotInitializer {

    @Value("${file.images.profile.github}")
    private String githubProfile;

    @Value("${github.username}")
    private String githubUsername;

    @Value("${file.images.profile.ai-reviewer}")
    private String aiReviewerProfile;

    @Value("${github.bot.username}")
    private String aiReviewBotUsername;

    private final MemberRepository memberRepository;

    @PostConstruct
    public void init() {

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

        Optional<Member> existingAiBot = memberRepository.findByUsername(aiReviewBotUsername);
        if (existingAiBot.isEmpty()) {
            memberRepository.save(Member.builder()
                    .username(aiReviewBotUsername)
                    .nickname(aiReviewBotUsername)
                    .profileImage(aiReviewerProfile)
                    .build());
            memberRepository.flush();
        }
    }
}