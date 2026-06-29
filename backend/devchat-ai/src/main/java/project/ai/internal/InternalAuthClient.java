package project.ai.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class InternalAuthClient {

    @Value("${internal.api.url}")
    private String apiUrl;

    private final WebClient.Builder webClientBuilder;
    private final InternalJwtProvider internalJwtProvider;

    public String getGithubToken(Long memberId) {
        return webClientBuilder.build()
                .get()
                .uri(apiUrl + "/internal/auth/github-token/" + memberId)
                .header("Authorization", "Bearer " + internalJwtProvider.issue())
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }
}