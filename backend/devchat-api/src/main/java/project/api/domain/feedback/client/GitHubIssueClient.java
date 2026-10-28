package project.api.domain.feedback.client;

import java.time.Duration;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 피드백을 DevChat 레포의 이슈로 만든다.
 * 토큰은 이 레포의 Issues 쓰기 권한만 준 fine-grained 토큰을 쓴다 (서버 .env의 FEEDBACK_GITHUB_TOKEN).
 */
@Component
public class GitHubIssueClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final WebClient client;
    private final String token;
    private final String repository;

    public GitHubIssueClient(@Value("${feedback.github.token:}") String token,
                             @Value("${feedback.github.repository:LimKangHyun/Devchat}") String repository,
                             @Value("${feedback.github.api-url:https://api.github.com}") String apiUrl) {
        this.client = WebClient.builder().baseUrl(apiUrl).build();
        this.token = token == null ? "" : token.trim();
        this.repository = repository == null ? "" : repository.trim();
    }

    public boolean isConfigured() {
        return !token.isEmpty() && repository.contains("/");
    }

    public CreatedIssue createIssue(String title, String body) {
        Map<String, Object> res = client.post()
                .uri("/repos/" + repository + "/issues")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("title", title, "body", body))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block(TIMEOUT);
        if (res == null) throw new IllegalStateException("GitHub 이슈 생성 응답이 비어 있음");
        return new CreatedIssue(((Number) res.get("number")).intValue(), (String) res.get("html_url"));
    }

    public record CreatedIssue(int number, String url) {
    }
}
