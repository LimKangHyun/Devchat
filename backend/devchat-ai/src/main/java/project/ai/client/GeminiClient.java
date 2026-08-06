package project.ai.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import project.common.dto.InlineReview;
import project.common.exception.errorcode.AiReviewErrorCode;
import project.common.exception.errorcode.IndexingErrorCode;
import project.common.exception.ex.AiReviewException;
import project.common.exception.ex.IndexingException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Slf4j
public class GeminiClient {

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;
    private final Validator validator;
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
    private String pushSummaryPrompt;

    public GeminiClient(WebClient.Builder webClientBuilder, ObjectMapper objectMapper,
        ResourceLoader resourceLoader) {
        this.webClientBuilder = webClientBuilder;
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
        this.validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @PostConstruct
    public void loadPrompts() throws IOException {
        inlineReviewPrompt    = loadPrompt("inline-review");
        issueSummaryPrompt    = loadPrompt("issue-summary");
        prSummaryPrompt       = loadPrompt("pr-summary");
        prReviewSummaryPrompt = loadPrompt("pr-review-summary");
        workflowSummaryPrompt = loadPrompt("workflow-summary");
        pushSummaryPrompt     = loadPrompt("push-summary");
    }

    @PostConstruct
    public void logReviewKeys() {
        apiKeys.forEach(k ->
            log.info("review key prefix={}", k.substring(0, 10))
        );
    }

    private String loadPrompt(String name) throws IOException {
        Resource resource = resourceLoader.getResource("classpath:prompts/" + name + ".txt");
        return resource.getContentAsString(StandardCharsets.UTF_8);
    }

    public List<InlineReview> reviewPrDiffInline(
        String diff, String ragContext, String fileContent, String prTitle, String prBody) {

        String truncatedDiff = truncate(diff, 4000);
        String truncatedContent = truncate(fileContent, 4000);

        String prInfo = "[PR 정보]\n제목: " + (prTitle != null ? prTitle : "") + "\n내용: " + (prBody != null ? prBody : "");

        StringBuilder promptBuilder = new StringBuilder(inlineReviewPrompt)
            .append("\n\n").append(prInfo);

        if (ragContext != null && !ragContext.isBlank()) {
            promptBuilder.append("\n\n").append(ragContext);
        }

        promptBuilder
            .append("\n\n[PR DIFF]\n").append(truncatedDiff)
            .append("\n\n[전체 파일 코드 - 앞의 숫자가 lineNumber]\n").append(addLineNumbers(truncatedContent));

        String response = callGemini(promptBuilder.toString());

        try {
            List<InlineReview> reviews = objectMapper.readValue(
                response, new TypeReference<List<InlineReview>>() {});

            return reviews.stream()
                .filter(review -> {
                    var violations = validator.validate(review);
                    if (!violations.isEmpty()) {
                        log.warn("[리뷰 검증 실패] violations={}", violations);
                        return false;
                    }
                    return true;
                })
                .toList();

        } catch (Exception e) {
            log.error("AI 인라인 리뷰 파싱 실패: {}", response, e);
            throw new AiReviewException(AiReviewErrorCode.GEMINI_RESPONSE_PARSE_FAILED);
        }
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
            ),
            "generationConfig", Map.of(
                "responseMimeType", "application/json"
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
                delay = handleUnexpectedException(e, attempt, totalAttempts, delay);
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

    public String summarizeGitEvent(String eventType, String prStatus, String fullContent) {
        String prompt = resolvePrompt(eventType, prStatus);
        try {
            return callGemini(prompt + "\n\n[이벤트 내용]\n" + fullContent);
        } catch (Exception e) {
            log.error("Gemini 요약 실패, 원본 반환", e);
            return fullContent;
        }
    }

    private String resolvePrompt(String eventType, String prStatus) {
        return switch (eventType) {
            case "ISSUE" -> issueSummaryPrompt;
            case "PULL_REQUEST" -> prStatus != null ? prSummaryPrompt : prReviewSummaryPrompt;
            case "WORKFLOW_RUN" -> workflowSummaryPrompt;
            case "PUSH" -> pushSummaryPrompt;
            default -> issueSummaryPrompt;
        };
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

    private long handleUnexpectedException(Exception e, int attempt, int totalAttempts, long delay) {
        log.warn("Gemini 예외 [시도 {}/{}]: {}", attempt, totalAttempts, e.getMessage());
        throwIfExhausted(attempt, totalAttempts, e);
        backoff(delay);
        return delay * 2;
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