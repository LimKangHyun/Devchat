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
import java.time.Duration;
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

    /** 리뷰·요약 생성 응답 제한(시도 1회 기준). 넘기면 다른 키로 재시도한다. */
    @Value("${gemini.review-timeout:60s}")
    private Duration reviewTimeout;

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

    static final String CONVENTIONS_BEGIN = "<<<팀 규칙 시작>>>";
    static final String CONVENTIONS_END = "<<<팀 규칙 끝>>>";

    /**
     * 방장이 정한 팀 규칙을 구분선으로 감싼다. 프롬프트의 [팀 규칙 활용 규칙]이 이 구간을 리뷰 기준으로만 쓰게 한다.
     * 규칙 안에 구분선 문자열을 넣어 구간을 빠져나가지 못하도록 미리 지운다.
     */
    static String teamConventionsSection(String conventions) {
        if (conventions == null || conventions.isBlank()) return "";
        String body = conventions.replace(CONVENTIONS_BEGIN, "").replace(CONVENTIONS_END, "").strip();
        if (body.isEmpty()) return "";
        return "[팀 규칙]\n" + CONVENTIONS_BEGIN + "\n" + body + "\n" + CONVENTIONS_END;
    }

    public List<InlineReview> reviewPrDiffInline(
        String diff, String ragContext, String relatedChangedCode,
        String fileContent, String prTitle, String prBody, String reviewConventions) {

        String truncatedDiff = truncate(diff, 4000);
        String numberedContent = numberedFileContent(fileContent);

        String prInfo = "[PR 정보]\n제목: " + (prTitle != null ? prTitle : "") + "\n내용: " + (prBody != null ? prBody : "");

        StringBuilder promptBuilder = new StringBuilder(inlineReviewPrompt);
        // 팀 규칙은 리뷰 기준이라 코드·PR 정보보다 앞에 둔다.
        String conventionsSection = teamConventionsSection(reviewConventions);
        if (!conventionsSection.isEmpty()) {
            promptBuilder.append("\n\n").append(conventionsSection);
        }
        promptBuilder.append("\n\n").append(prInfo);

        if (ragContext != null && !ragContext.isBlank()) {
            promptBuilder.append("\n\n").append(ragContext);
        }
        if (relatedChangedCode != null && !relatedChangedCode.isBlank()) {
            promptBuilder.append("\n\n").append(relatedChangedCode);
        }

        promptBuilder
            .append("\n\n[PR DIFF]\n").append(truncatedDiff)
            .append("\n\n[전체 파일 코드 - 앞의 숫자가 lineNumber]\n").append(numberedContent);

        // 원문(diff, 응답)은 사용자 코드가 그대로 담겨 있어 DEBUG로만 남긴다.
        log.debug("[Gemini 프롬프트 diff 부분]\n{}", truncatedDiff);

        String prompt = promptBuilder.toString();
        long start = System.currentTimeMillis();
        String response = callGemini(prompt, true);
        long elapsedMs = System.currentTimeMillis() - start;
        log.debug("[Gemini 원본 응답] {}", response);

        try {
            List<InlineReview> reviews = objectMapper.readValue(
                response, new TypeReference<List<InlineReview>>() {});

            List<InlineReview> valid = reviews.stream()
                .filter(review -> {
                    var violations = validator.validate(review);
                    if (!violations.isEmpty()) {
                        log.warn("[리뷰 검증 실패] violations={}", violations);
                        return false;
                    }
                    return true;
                })
                .toList();

            // 튜닝용: 프롬프트 크기와 섹션 유무, 코멘트 수. 맥락·팀 규칙이 코멘트 수에 주는 영향을 보려고 남긴다.
            log.info("[리뷰-요약] 프롬프트={}자 (맥락={}자, 함께변경={}자, 팀규칙={}), 응답={}ms, 코멘트={}개 (검증통과={}개)",
                prompt.length(), ragContext == null ? 0 : ragContext.length(),
                relatedChangedCode == null ? 0 : relatedChangedCode.length(),
                !conventionsSection.isEmpty(), elapsedMs, reviews.size(), valid.size());
            return valid;

        } catch (Exception e) {
            // 응답 원문 대신 길이와 앞부분만 남긴다. 원문 전체는 DEBUG 로그에 있다.
            log.error("AI 인라인 리뷰 파싱 실패. 응답={}자, 앞부분={}",
                response == null ? 0 : response.length(),
                response == null ? "null" : response.substring(0, Math.min(200, response.length())), e);
            throw new AiReviewException(AiReviewErrorCode.GEMINI_RESPONSE_PARSE_FAILED);
        }
    }

    /**
     * 프롬프트에 넣을 파일 코드. 줄 번호를 한 번만 붙이고 10000자에서 자른다.
     * 예전에는 번호를 붙인 코드에 한 번 더 붙여 "   1:    1: 코드"처럼 들어갔다.
     */
    static String numberedFileContent(String fileContent) {
        return truncate(addLineNumbers(fileContent), 10000);
    }

    private static String truncate(String text, int limit) {
        return text.length() > limit ? text.substring(0, limit) + "\n...(truncated)" : text;
    }

    private static String addLineNumbers(String content) {
        String[] lines = content.split("\n");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            sb.append(String.format("%4d: %s\n", i + 1, lines[i]));
        }
        return sb.toString();
    }

    private String callGemini(String prompt, boolean jsonMode) {
        Map<String, Object> generationConfig = jsonMode
            ? Map.of("responseMimeType", "application/json")
            : Map.of();

        Map<String, Object> requestBody = Map.of(
            "contents", List.of(
                Map.of("parts", List.of(
                    Map.of("text", prompt)
                ))
            ),
            "generationConfig", generationConfig
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
            .block(reviewTimeout);

        return Objects.requireNonNull(response).candidates().get(0).content().parts().get(0).text();
    }

    public String summarizeGitEvent(String eventType, String prStatus, String fullContent) {
        String prompt = resolvePrompt(eventType, prStatus);
        try {
            // 요약은 프롬프트가 지시한 순수 텍스트(이모지+줄바꿈+2~3줄) 그대로 필요 → jsonMode=false
            return callGemini(prompt + "\n\n[이벤트 내용]\n" + fullContent, false);
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