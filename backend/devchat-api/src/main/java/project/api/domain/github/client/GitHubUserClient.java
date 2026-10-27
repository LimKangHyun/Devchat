package project.api.domain.github.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import project.api.global.exception.errorcode.GitHubErrorCode;
import project.api.global.exception.ex.GitHubException;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Slf4j
public class GitHubUserClient {

    private final WebClient.Builder webClientBuilder;

    /** webhook 서명용 secret. 비어 있으면 secret 없이 등록한다(로컬 개발). */
    @Value("${github.webhook.secret:}")
    private String webhookSecret;

    /** push는 기본 브랜치 직접 push를 재인덱싱하려고 받는다. 채팅 메시지로는 만들지 않는다. */
    private static final List<String> WEBHOOK_EVENTS =
        List.of("issues", "pull_request", "pull_request_review", "workflow_run", "push");

    private Map<String, Object> webhookConfig(String webhookUrl) {
        Map<String, Object> config = new HashMap<>();
        config.put("url", webhookUrl);
        config.put("content_type", "json");
        config.put("insecure_ssl", "0");
        if (webhookSecret != null && !webhookSecret.isBlank()) {
            config.put("secret", webhookSecret);
        }
        return config;
    }

    public boolean validateAdminPermission(String accessToken, String owner, String repo) {
        String url = "https://api.github.com/repos/" + owner + "/" + repo; //요청할 api

        Map<String, Object> response = webClientBuilder.build()
            .get()
            .uri(url)
            .header("Authorization", "Bearer " + accessToken)
            .retrieve()
            .onStatus(HttpStatusCode::is4xxClientError, error ->
                error.bodyToMono(String.class)
                    .flatMap(errorBody -> {
                        if (error.statusCode() == HttpStatus.UNAUTHORIZED) {
                            log.error(errorBody);
                            return Mono.error(new GitHubException(GitHubErrorCode.INVALID_TOKEN));
                        } else if (error.statusCode() == HttpStatus.NOT_FOUND) {
                            log.error(errorBody);
                            return Mono.error(new GitHubException(GitHubErrorCode.REPO_NOT_FOUND));
                        } else {
                            log.error("GitHubErrorCode.CLIENT_ERROR: {}", errorBody);
                            return Mono.error(new GitHubException(GitHubErrorCode.CLIENT_ERROR));
                        }
                    })
            )
            .onStatus(HttpStatusCode::is5xxServerError, error ->
                error.bodyToMono(String.class)
                    .flatMap(errorBody -> {
                        log.error("GitHubErrorCode.CLIENT_ERROR: {}", errorBody);
                        return Mono.error(new GitHubException(GitHubErrorCode.SERVER_ERROR));
                    })
            )
            .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {
            })
            .block();

        // jon 응답 구조에서 "permissions": {"admin": true} 이면 레포 접근 권한 있는거임
        if (response == null || !response.containsKey("permissions")) {
            throw new GitHubException(GitHubErrorCode.UNEXPECTED_RESPONSE);
        }

        Map<String, Boolean> permissions = (Map<String, Boolean>) response.get("permissions");
        boolean isAdmin = permissions.getOrDefault("admin", false);

        if (!isAdmin) {
            throw new GitHubException(GitHubErrorCode.UNAUTHORIZED_REPO);
        }

        return true;
    }

    public Long registerWebhook(String accessToken, String owner, String repo, String webhookUrl) {
        log.info("owner={}", owner);
        log.info("repo={}", repo);
        log.info("url=https://api.github.com/repos/{}/{}/hooks", owner, repo);
        String apiUrl = "https://api.github.com/repos/" + owner + "/" + repo + "/hooks";

        Map<String, Object> requestBody = Map.of(
            "name", "web",
            "active", true,
            "events", WEBHOOK_EVENTS,
            "config", webhookConfig(webhookUrl)
        );

        try {
            Map<String, Object> response = webClientBuilder.build()
                .post()
                .uri(apiUrl)
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/vnd.github.v3+json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {
                })
                .block();

            Number idNumber = (Number) response.get("id");
            return idNumber.longValue();
        } catch (WebClientResponseException e) {
            log.error("GitHub webhook 등록 실패. status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw new GitHubException(GitHubErrorCode.WEBHOOK_REGISTER_FAILED);
        } catch (Exception e) {
            log.error("GitHub webhook 등록 실패(예외 유형: {})", e.getClass().getSimpleName(), e);
            throw new GitHubException(GitHubErrorCode.WEBHOOK_REGISTER_FAILED);
        }
    }

    /**
     * 이미 등록된 webhook의 설정(secret, 이벤트 목록, URL)을 지금 기준으로 다시 맞춘다.
     * secret 없이 등록된 예전 webhook에 서명을 켜고, push 이벤트를 추가할 때 쓴다.
     *
     * @return 갱신했으면 true, webhook이 GitHub에서 지워져 없으면 false (호출한 쪽이 새로 등록한다)
     */
    public boolean updateWebhook(String accessToken, String owner, String repo, Long webhookId, String webhookUrl) {
        String apiUrl = "https://api.github.com/repos/" + owner + "/" + repo + "/hooks/" + webhookId;
        Map<String, Object> requestBody = Map.of(
            "active", true,
            "events", WEBHOOK_EVENTS,
            "config", webhookConfig(webhookUrl)
        );
        try {
            webClientBuilder.build()
                .patch()
                .uri(apiUrl)
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/vnd.github.v3+json")
                .bodyValue(requestBody)
                .retrieve()
                .toBodilessEntity()
                .block();
            return true;
        } catch (WebClientResponseException.NotFound e) {
            log.warn("갱신할 webhook이 GitHub에 없음. owner={}, repo={}, webhookId={}", owner, repo, webhookId);
            return false;
        } catch (WebClientResponseException e) {
            log.error("GitHub webhook 갱신 실패. status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw new GitHubException(GitHubErrorCode.WEBHOOK_REGISTER_FAILED);
        }
    }

    public void deleteWebhook(String accessToken, String owner, String repo, Long webhookId) {
        String apiUrl =
                "https://api.github.com/repos/" + owner + "/" + repo + "/hooks/" + webhookId;

        try {
            webClientBuilder.build()
                    .delete()
                    .uri(apiUrl)
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Accept", "application/vnd.github.v3+json")
                    .retrieve()
                    .onStatus(status -> status.value() == 404, error -> {
                        log.info("webhook이 이미 존재하지 않음. 삭제 스킵. url={}", apiUrl);
                        return Mono.empty();
                    })
                    .onStatus(HttpStatusCode::is4xxClientError, error ->
                            error.bodyToMono(String.class)
                                    .flatMap(errorBody -> {
                                        log.error("webhook 삭제 4xx. url={}, body={}", apiUrl, errorBody);
                                        return Mono.error(new GitHubException(GitHubErrorCode.WEBHOOK_DELETE_FAILED));
                                    })
                    )
                    .toBodilessEntity()
                    .block();
        } catch (Exception e) {
            log.error("webhook 삭제 실패. url={}, message={}", apiUrl, e.getMessage());
            throw new GitHubException(GitHubErrorCode.WEBHOOK_DELETE_FAILED);
        }
    }
}