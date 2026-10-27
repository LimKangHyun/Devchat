package project.api.auth.api;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;
import project.api.auth.dto.MemberDetails;
import project.api.auth.jwt.JwtProvider;
import project.api.domain.member.dao.MemberRepository;

import java.util.List;

/**
 * 부하 테스트용 토큰 일괄 발급. local 프로파일에서만 활성화된다.
 */
@Profile("local")
@RestController
@RequestMapping("/test")
@RequiredArgsConstructor
public class LoadTestTokenController {

    private final MemberRepository memberRepository;
    private final JwtProvider jwtProvider;

    @GetMapping("/tokens")
    public List<String> issueTokens(@RequestParam(defaultValue = "50") int count) {
        return memberRepository.findAll(PageRequest.of(0, count))
                .map(m -> new MemberDetails(
                        m.getId(), m.getUsername(), m.getNickname(), m.getProfileImage()))
                .map(md -> jwtProvider.generateTokenPair(md).accessToken())
                .toList();
    }
}