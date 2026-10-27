package project.api.domain.github.client;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
@RequiredArgsConstructor
@Slf4j
public class GitHubBotClient {

    private final WebClient.Builder webClientBuilder;

    @Value("${github.bot.pat}")
    private String botPat;

    public String getPrDiff(String owner, String repo, int prNumber) {
        WebClient client = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();

        String diff = client.get()
                .uri("https://api.github.com/repos/" + owner + "/" + repo + "/pulls/" + prNumber)
                .header("Authorization", "Bearer " + botPat)
                .header("Accept", "application/vnd.github.v3.diff")
                .retrieve()
                .bodyToMono(String.class)
                .block();

        log.info("PR diff: {}", diff);
        return diff;
    }

    public String getFileContent(String owner, String repo, String path, String ref) {
        Map<String, Object> response = webClientBuilder.build()
            .get()
            .uri("https://api.github.com/repos/" + owner + "/" + repo + "/contents/" + path + "?ref=" + ref)
            .header("Authorization", "Bearer " + botPat)
            .header("Accept", "application/vnd.github+json")
            .retrieve()
            .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
            .block();

        String encoded = (String) response.get("content");
        String content = new String(Base64.getDecoder().decode(encoded.replaceAll("\\s", "")));
        log.info("파일 내용 [{}]:\n{}", path, content);
        return content;
    }

    public String getHeadSha(String owner, String repo, int prNumber) {
        Map<String, Object> response = webClientBuilder.build()
            .get()
            .uri("https://api.github.com/repos/" + owner + "/" + repo + "/pulls/" + prNumber)
            .header("Authorization", "Bearer " + botPat)
            .header("Accept", "application/vnd.github+json")
            .retrieve()
            .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
            .block();
        Map<String, Object> head = (Map<String, Object>) response.get("head");
        return (String) head.get("sha");
    }

    public String getBaseSha(String owner, String repo, int prNumber) {
        Map<String, Object> response = webClientBuilder.build()
            .get()
            .uri("https://api.github.com/repos/" + owner + "/" + repo + "/pulls/" + prNumber)
            .header("Authorization", "Bearer " + botPat)
            .header("Accept", "application/vnd.github+json")
            .retrieve()
            .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
            .block();
        Map<String, Object> base = (Map<String, Object>) response.get("base");
        return (String) base.get("sha");
    }

    public void postReviewComment(String owner, String repo, int prNumber, String body) {
        webClientBuilder.build()
            .post()
            .uri("https://api.github.com/repos/" + owner + "/" + repo + "/pulls/" + prNumber + "/reviews")
            .header("Authorization", "Bearer " + botPat)
            .header("Accept", "application/vnd.github+json")
            .bodyValue(Map.of(
                "body", body,
                "event", "COMMENT"
            ))
            .retrieve()
            .toBodilessEntity()
            .block();

        log.info("GitHub PR 리뷰 등록 완료: PR #{}", prNumber);
    }

    public void postInlineReviews(String owner, String repo, int prNumber, List<Map<String, Object>> comments) {
        webClientBuilder.build()
            .post()
            .uri("https://api.github.com/repos/" + owner + "/" + repo + "/pulls/" + prNumber + "/reviews")
            .header("Authorization", "Bearer " + botPat)
            .header("Accept", "application/vnd.github+json")
            .bodyValue(Map.of(
                "event", "COMMENT",
                "comments", comments
            ))
            .retrieve()
            .toBodilessEntity()
            .block();

        log.info("GitHub 인라인 리뷰 등록 완료: PR #{}", prNumber);
    }

    /** PR 변경 파일. previousFilename은 status가 renamed일 때만 값이 있다. */
    public record PrFile(String filename, String status, String previousFilename) {}

    public Map<String, String> getPrFileStatuses(String owner, String repo, int prNumber) {
        return getPrFiles(owner, repo, prNumber).stream()
                .collect(Collectors.toMap(PrFile::filename, PrFile::status));
    }

    /** 두 커밋 사이에 바뀐 파일. GitHub compare API는 파일을 최대 300개까지 준다. */
    public List<PrFile> getCompareFiles(String owner, String repo, String base, String head) {
        Map<String, Object> body = webClientBuilder.build()
                .get()
                .uri("https://api.github.com/repos/" + owner + "/" + repo + "/compare/" + base + "..." + head)
                .header("Authorization", "Bearer " + botPat)
                .header("Accept", "application/vnd.github+json")
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();

        List<Map<String, Object>> files = body == null ? null : (List<Map<String, Object>>) body.get("files");
        if (files == null) return List.of();
        return files.stream()
                .map(f -> new PrFile(
                        (String) f.get("filename"),
                        (String) f.get("status"),
                        (String) f.get("previous_filename")))
                .toList();
    }

    /** 이 커밋이 머지된 PR에서 나왔는지. 기본 브랜치 push 중 PR 머지로 생긴 것을 걸러낼 때 쓴다. */
    public boolean isFromMergedPullRequest(String owner, String repo, String sha) {
        List<Map<String, Object>> pulls = webClientBuilder.build()
                .get()
                .uri("https://api.github.com/repos/" + owner + "/" + repo + "/commits/" + sha + "/pulls")
                .header("Authorization", "Bearer " + botPat)
                .header("Accept", "application/vnd.github+json")
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
                .block();

        return pulls != null && pulls.stream().anyMatch(p -> p.get("merged_at") != null);
    }

    public List<PrFile> getPrFiles(String owner, String repo, int prNumber) {
        List<Map<String, Object>> files = webClientBuilder.build()
                .get()
                .uri("https://api.github.com/repos/" + owner + "/" + repo + "/pulls/" + prNumber + "/files")
                .header("Authorization", "Bearer " + botPat)
                .header("Accept", "application/vnd.github+json")
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
                .block();

        return files.stream()
                .map(f -> new PrFile(
                        (String) f.get("filename"),
                        (String) f.get("status"),
                        (String) f.get("previous_filename")))
                .toList();
    }
}