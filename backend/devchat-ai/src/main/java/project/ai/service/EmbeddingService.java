package project.ai.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import project.common.exception.errorcode.IndexingErrorCode;
import project.common.exception.ex.IndexingException;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final WebClient.Builder webClientBuilder;

    @Value("#{'${gemini.embedding-api-keys}'.split(',')}")
    private List<String> apiKeys;

    private final AtomicInteger embedCallCount = new AtomicInteger(0);
    private final AtomicInteger keyIndex = new AtomicInteger(0);

    private static final String BATCH_EMBEDDING_URL =
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-embedding-001:batchEmbedContents";

    private static final String COUNT_TOKENS_URL =
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-embedding-001:countTokens";

    /**
     * Gemini 429 응답의 RetryInfo.retryDelay 값을 파싱하기 위한 정규식.
     * 형식 예: "retryDelay": "14.480648795s"
     */
    private static final Pattern RETRY_DELAY_PATTERN =
        Pattern.compile("\"retryDelay\"\\s*:\\s*\"(\\d+(?:\\.\\d+)?)s\"");

    /** 429 응답에서 quotaId를 뽑기 위한 정규식. 예: "quotaId": "EmbedContentRequestsPerMinutePerUserPerProjectPerModel-FreeTier" */
    private static final Pattern QUOTA_ID_PATTERN =
        Pattern.compile("\"quotaId\"\\s*:\\s*\"([^\"]+)\"");

    /** 429 응답에서 quotaValue를 뽑기 위한 정규식. 예: "quotaValue": "100" */
    private static final Pattern QUOTA_VALUE_PATTERN =
        Pattern.compile("\"quotaValue\"\\s*:\\s*\"([^\"]+)\"");

    /** 429 응답에서 quotaMetric을 뽑기 위한 정규식. 예: "quotaMetric": "generativelanguage.googleapis.com/embed_content_free_tier_requests" */
    private static final Pattern QUOTA_METRIC_PATTERN =
        Pattern.compile("\"quotaMetric\"\\s*:\\s*\"([^\"]+)\"");

    /** retryDelay 파싱 실패 시 사용할 안전한 기본 대기시간(ms) */
    private static final long DEFAULT_RETRY_DELAY_MS = 15_000L;

    /** 서버가 알려준 시간에 더해줄 여유 buffer(ms) - 파싱~재요청 사이 흐르는 시간 보정 */
    private static final long RETRY_DELAY_BUFFER_MS = 500L;

    @PostConstruct
    public void logKeys() {
        log.debug("embedding keys: {}", apiKeys);
        for (int i = 0; i < apiKeys.size(); i++) {
            log.info("[키등록] index={}, key={}", i, maskKey(apiKeys.get(i)));
        }
    }

    public float[] embed(String text) {
        return embedBatch(List.of(text)).get(0);
    }

    /**
     * 청크 텍스트 리스트의 총 토큰 수를 실측한다.
     * AST 청킹 전/후 비교 실험용 — 실제 임베딩 API에 보내는 텍스트 기준으로 측정.
     */
    public int countTokens(List<String> texts) {
        String combined = String.join("\n", texts);
        Map<String, Object> requestBody = Map.of(
            "contents", List.of(
                Map.of(
                    "parts", List.of(
                        Map.of("text", combined)
                    )
                )
            )
        );

        int idx = keyIndex.getAndIncrement() % apiKeys.size();
        String key = apiKeys.get(idx);

        CountTokensResponse response = webClientBuilder.build()
            .post()
            .uri(COUNT_TOKENS_URL + "?key=" + key)
            .header("Content-Type", "application/json")
            .bodyValue(requestBody)
            .retrieve()
            .bodyToMono(CountTokensResponse.class)
            .block();

        if (response == null) {
            throw new IndexingException(IndexingErrorCode.EMBEDDING_INVALID_REQUEST);
        }

        log.info("[토큰측정] keyIndex={}, 청크 {}개, 총 토큰 수={}", idx, texts.size(), response.totalTokens());
        return response.totalTokens();
    }

    private record CountTokensResponse(int totalTokens) {}

    public List<float[]> embedBatch(List<String> texts) {
        Map<String, Object> requestBody = buildRequestBody(texts);
        int totalAttempts = apiKeys.size() * 3;
        long delay = 1000;
        Exception lastException = null;

        for (int attempt = 1; attempt <= totalAttempts; attempt++) {
            try {
                return callEmbeddingApi(requestBody);
            } catch (WebClientResponseException e) {
                lastException = e;
                delay = handleApiException(e, attempt, totalAttempts, delay);
            } catch (Exception e) {
                lastException = e;
                delay = handleUnexpectedException(e, attempt, totalAttempts, delay);
            }
        }
        log.error("embedBatch 모든 재시도 소진 [{}회]", totalAttempts);
        throw new IndexingException(IndexingErrorCode.EMBEDDING_EXHAUSTED, lastException);
    }

    private List<float[]> callEmbeddingApi(Map<String, Object> requestBody) {
        long apiStart = System.currentTimeMillis();

        int idx = keyIndex.getAndIncrement() % apiKeys.size();
        String key = apiKeys.get(idx);

        log.info("[{}] Gemini embedding API 호출 시작. keyIndex={}, key={}",
            Thread.currentThread().getName(), idx, maskKey(key));

        try {
            BatchEmbeddingResponse response = webClientBuilder
                .codecs(c -> c.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build()
                .post()
                .uri(BATCH_EMBEDDING_URL + "?key=" + key)
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(BatchEmbeddingResponse.class)
                .block();

            log.info("[{}] Gemini embedding API 응답 수신. keyIndex={}, 소요={}ms",
                Thread.currentThread().getName(), idx, System.currentTimeMillis() - apiStart);

            if (response == null || response.embeddings() == null) {
                throw new IndexingException(IndexingErrorCode.EMBEDDING_INVALID_REQUEST);
            }

            embedCallCount.incrementAndGet();

            return toFloatArrays(response.embeddings());
        } catch (WebClientResponseException e) {
            // 어느 키(프로젝트)에서 실패했는지 반드시 함께 기록 - quotaId/Value는 handleApiException에서 추가 파싱
            log.warn("[{}] Gemini embedding API 실패. keyIndex={}, key={}, status={}",
                Thread.currentThread().getName(), idx, maskKey(key), e.getStatusCode().value());
            throw e;
        }
    }

    private long handleUnexpectedException(Exception e, int attempt, int totalAttempts, long delay) {
        log.warn("embedBatch 예외 [시도 {}/{}]: {}", attempt, totalAttempts, e.getMessage());
        throwIfExhausted(e, attempt, totalAttempts);
        backoff(delay);
        return delay * 2;
    }

    private long handleApiException(WebClientResponseException e, int attempt, int totalAttempts, long delay) {
        int status = e.getStatusCode().value();
        String responseBody = e.getResponseBodyAsString();

        if (status == 400 || status == 401 || status == 403) {
            log.error("embedBatch 치명적 오류 [시도 {}/{}]: status={}, body={}",
                attempt, totalAttempts, status, responseBody);
            throw new IndexingException(IndexingErrorCode.EMBEDDING_FAILED, e);
        }
        if (status == 429) {
            long retryDelayMs = parseRetryDelayMs(responseBody);
            String quotaId = extractField(responseBody, QUOTA_ID_PATTERN);
            String quotaValue = extractField(responseBody, QUOTA_VALUE_PATTERN);
            String quotaMetric = extractField(responseBody, QUOTA_METRIC_PATTERN);

            log.warn("embedBatch 429 [시도 {}/{}] quotaMetric={}, quotaId={}, quotaValue={}, retryDelay={}ms",
                attempt, totalAttempts, quotaMetric, quotaId, quotaValue, retryDelayMs);
            log.warn("[429 원본 응답] {}", responseBody);

            backoff(retryDelayMs);
            return retryDelayMs;
        }

        log.warn("embedBatch 5xx 오류 [시도 {}/{}]: status={}, body={}", attempt, totalAttempts, status, responseBody);
        throwIfExhausted(e, attempt, totalAttempts);
        backoff(delay);
        return delay * 2;
    }

    /**
     * Gemini 429 응답 본문에서 RetryInfo.retryDelay 값을 파싱한다.
     * 형식: "retryDelay": "14.480648795s"
     * 파싱 실패 시 안전한 기본값을 반환한다.
     */
    private long parseRetryDelayMs(String responseBody) {
        if (responseBody == null) return DEFAULT_RETRY_DELAY_MS;

        try {
            Matcher matcher = RETRY_DELAY_PATTERN.matcher(responseBody);
            if (matcher.find()) {
                double seconds = Double.parseDouble(matcher.group(1));
                return (long) (seconds * 1000) + RETRY_DELAY_BUFFER_MS;
            }
        } catch (Exception e) {
            log.warn("retryDelay 파싱 실패, 기본값({}) 사용. body={}", DEFAULT_RETRY_DELAY_MS, responseBody, e);
        }
        return DEFAULT_RETRY_DELAY_MS;
    }

    private String extractField(String responseBody, Pattern pattern) {
        if (responseBody == null) return "unknown";
        Matcher matcher = pattern.matcher(responseBody);
        return matcher.find() ? matcher.group(1) : "unknown";
    }

    /** 로그에 키 전체를 남기지 않도록 앞 6자리만 노출 */
    private String maskKey(String key) {
        if (key == null || key.length() < 6) return "****";
        return key.substring(0, 6) + "****";
    }

    private void throwIfExhausted(Exception e, int attempt, int totalAttempts) {
        if (attempt == totalAttempts) {
            throw new IndexingException(IndexingErrorCode.EMBEDDING_FAILED, e);
        }
    }

    private void backoff(long delay) {
        long jitter = (long) (Math.random() * 300);
        sleep(delay + jitter);
    }

    private Map<String, Object> buildRequestBody(List<String> texts) {
        List<Map<String, Object>> requests = texts.stream()
            .map(text -> Map.<String, Object>of(
                "model", "models/gemini-embedding-001",
                "content", Map.of("parts", List.of(Map.of("text", text)))
            ))
            .toList();
        return Map.of("requests", requests);
    }

    private List<float[]> toFloatArrays(List<BatchEmbeddingResponse.Embedding> embeddings) {
        return embeddings.stream()
            .map(embedding -> {
                List<Float> values = embedding.values();
                float[] arr = new float[values.size()];
                for (int i = 0; i < values.size(); i++) arr[i] = values.get(i);
                return arr;
            })
            .toList();
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("sleep 인터럽트", e);
        }
    }

    private record BatchEmbeddingResponse(List<Embedding> embeddings) {
        record Embedding(List<Float> values) {}
    }

    public int getAndResetCount() {
        return embedCallCount.getAndSet(0);
    }
}