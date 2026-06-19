package project.backend.domain.aireview.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import project.backend.domain.aireview.dto.InlineReview;
import project.backend.domain.github.dto.GitMessageDto;
import project.backend.global.exception.errorcode.IndexingErrorCode;
import project.backend.global.exception.ex.IndexingException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@RequiredArgsConstructor
@Slf4j
public class GeminiClient {

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;
    private final AtomicInteger keyIndex = new AtomicInteger(0);

    @Value("#{'${gemini.review-api-keys}'.split(',')}")
    private List<String> apiKeys;

    private static final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent";

    private String inlineReviewPrompt;
    private String issueSummaryPrompt;
    private String prSummaryPrompt;
    private String prReviewSummaryPrompt;
    private String workflowSummaryPrompt;

    @PostConstruct
    public void loadPrompts() throws IOException {
        inlineReviewPrompt    = loadPrompt("inline-review");
        issueSummaryPrompt    = loadPrompt("issue-summary");
        prSummaryPrompt       = loadPrompt("pr-summary");
        prReviewSummaryPrompt = loadPrompt("pr-review-summary");
        workflowSummaryPrompt = loadPrompt("workflow-summary");
    }

    private String loadPrompt(String name) throws IOException {
        Resource resource = resourceLoader.getResource("classpath:prompts/" + name + ".txt");
        return resource.getContentAsString(StandardCharsets.UTF_8);
    }

    public List<InlineReview> reviewPrDiffInline(String diff, String fileContent, String prTitle, String prBody) {
        String truncatedDiff    = truncate(diff, 4000);
        String truncatedContent = truncate(fileContent, 4000);

        String prInfo = "[PR 정보]\n제목: " + (prTitle != null ? prTitle : "") + "\n내용: " + (prBody != null ? prBody : "");

        String prompt = inlineReviewPrompt
                + "\n\n" + prInfo
                + "\n\n[PR DIFF]\n" + truncatedDiff
                + "\n\n[전체 파일 코드 - 앞의 숫자가 lineNumber]\n" + addLineNumbers(truncatedContent);

        String response = callGemini(prompt);

        try {
            String cleaned = response.replaceAll("```json", "").replaceAll("```", "").trim();
            return objectMapper.readValue(cleaned, new TypeReference<List<InlineReview>>() {});
        } catch (Exception e) {
            log.error("AI 인라인 리뷰 파싱 실패: {}", response, e);
            return List.of();
        }
    }

    public String summarizeGitEvent(GitMessageDto gitMessage) {
        String rawContent = gitMessage.getFullContent() != null
                ? gitMessage.getFullContent()
                : gitMessage.getContent();

        String prompt = resolvePrompt(gitMessage);

        try {
            return callGemini(prompt + "\n\n[이벤트 내용]\n" + rawContent);
        } catch (Exception e) {
            log.error("Gemini 요약 실패, 원본 반환", e);
            return rawContent;
        }
    }

    private String resolvePrompt(GitMessageDto gitMessage) {
        return switch (gitMessage.getType()) {
            case ISSUE -> issueSummaryPrompt;
            case PULL_REQUEST -> gitMessage.getPrStatus() != null
                    ? prSummaryPrompt
                    : prReviewSummaryPrompt;
            case WORKFLOW_RUN -> workflowSummaryPrompt;
        };
    }

    private String truncate(String text, int limit) {
        return text.length() > limit ? text.substring(0, limit) + "\n...(truncated)" : text;
    }

    private String addLineNumbers(String content) {
        String[] lines = content.split("\n");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            sb.append(String.format("%4d: %s\n", i + 1, lines[i]));
        }
        return sb.toString();
    }

    private String callGemini(String prompt) {
        Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(
                                Map.of("text", prompt)
                        ))
                )
        );

        int totalAttempts = apiKeys.size() * 2;
        long delay = 1000;

        for (int attempt = 1; attempt <= totalAttempts; attempt++) {
            try {
                return callGeminiApi(requestBody);
            } catch (WebClientResponseException e) {
                delay = handleApiException(e, attempt, totalAttempts, delay);
            } catch (Exception e) {
                handleUnexpectedException(e, attempt, totalAttempts);
            }
        }
        throw new IndexingException(IndexingErrorCode.EMBEDDING_EXHAUSTED);
    }

    private String callGeminiApi(Map<String, Object> requestBody) {
        GeminiResponse response = webClientBuilder.build()
                .post()
                .uri(GEMINI_URL + "?key=" + nextKey())
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(GeminiResponse.class)
                .block();

        return Objects.requireNonNull(response).candidates().get(0).content().parts().get(0).text();
    }

    private long handleApiException(WebClientResponseException e, int attempt, int totalAttempts, long delay) {
        int status = e.getStatusCode().value();

        if (status == 400 || status == 401 || status == 403) {
            throw new IndexingException(IndexingErrorCode.EMBEDDING_INVALID_REQUEST);
        }

        if (status == 429) {
            log.warn("Gemini 429 - 키 로테이션 [시도 {}/{}]", attempt, totalAttempts);
            return delay;
        }

        log.warn("Gemini 5xx 오류 [시도 {}/{}]: status={}", attempt, totalAttempts, status);
        throwIfExhausted(attempt, totalAttempts, e);
        backoff(delay);
        return delay * 2;
    }

    private void handleUnexpectedException(Exception e, int attempt, int totalAttempts) {
        log.warn("Gemini 예외 [시도 {}/{}]: {}", attempt, totalAttempts, e.getMessage());
        throwIfExhausted(attempt, totalAttempts, e);
    }

    private void throwIfExhausted(int attempt, int totalAttempts, Exception e) {
        if (attempt == totalAttempts) {
            throw new IndexingException(IndexingErrorCode.EMBEDDING_EXHAUSTED);
        }
    }

    private void backoff(long delay) {
        long jitter = (long) (Math.random() * 300);
        sleep(delay + jitter);
    }

    private String nextKey() {
        return apiKeys.get(keyIndex.getAndIncrement() % apiKeys.size());
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("sleep 인터럽트", e);
        }
    }

    private record GeminiResponse(List<Candidate> candidates) {
        record Candidate(Content content) {}
        record Content(List<Part> parts) {}
        record Part(String text) {}
    }
}
