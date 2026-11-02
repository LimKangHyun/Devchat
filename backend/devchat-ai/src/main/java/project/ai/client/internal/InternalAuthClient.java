package project.ai.client.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;

@Slf4j
@Component
@RequiredArgsConstructor
public class InternalAuthClient {

    private static final String GITHUB_TOKEN_PATH = "/internal/auth/github-token";

    private final InternalApiProperties internalApi;
    private final WebClient.Builder webClientBuilder;
    private final InternalJwtProvider internalJwtProvider;

    public String getGithubToken(Long memberId) {
        return webClientBuilder.build()
                .get()
                .uri(internalApi.url() + GITHUB_TOKEN_PATH)
                .header(
                    "Authorization",
                    "Bearer " + internalJwtProvider.issue(memberId)
                )
                .retrieve()
                .bodyToMono(String.class)
                // 연결 자체가 안 될 때(주소·DNS·포트 문제) 어디로 보냈는지 바로 보이게 한다
                .doOnError(WebClientRequestException.class, e -> log.error(
                    "내부 API 연결 실패 - host={}, path={}, cause={}",
                    internalApi.host(), GITHUB_TOKEN_PATH, e.getMostSpecificCause().getMessage()))
                .block();
    }
}
