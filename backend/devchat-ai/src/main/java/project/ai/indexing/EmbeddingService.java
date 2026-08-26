package project.ai.indexing;

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

    /**
     * 인덱싱(코퍼스) 임베딩에 쓸 taskType.
     * "이 텍스트는 나중에 검색당할 문서다"를 모델에 알려준다.
     */
    @Value("${gemini.embedding.document-task-type:RETRIEVAL_DOCUMENT}")
    private String documentTaskType;

    /**
     * 검색 쿼리 임베딩에 쓸 taskType.
     * 코드 검색 전용 타입이 엔드포인트에서 거부될 경우를 대비해 설정값으로 분리했다.
     * 400(INVALID_ARGUMENT)이 나면 application.yml에서 RETRIEVAL_QUERY로 내리면 된다.
     */
    @Value("${gemini.embedding.query-task-type:CODE_RETRIEVAL_QUERY}")
    private String queryTaskType;

    private final AtomicInteger embedCallCount = new AtomicInteger(0);
    private final AtomicInteger keyIndex = new AtomicInteger(0);

    private static final String BATCH_EMBEDDING_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-embedding-001:batchEmbedContents";

    private static final String COUNT_TOKENS_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-embedding-001:countTokens";

    private static final String EMBEDDING_MODEL = "models/gemini-embedding-001";

    /**
     * Gemini 429 응답의 RetryInfo.retryDelay 값을 파싱하기 위한 정규식.
     * 형식 예: "retryDelay": "14.480648795s"
     */
    private static final Pattern RETRY_DELAY_PATTERN =
            Pattern.compile("\"retryDelay\"\\s*:\\s*\"(\\d+(?:\\.\\d+)?)s\"");

    /** 429 응답에서 quotaId를 뽑기 위한 정규식. */
    private static final Pattern QUOTA_ID_PATTERN =
            Pattern.compile("\"quotaId\"\\s*:\\s*\"([^\"]+)\"");

    /** 429 응답에서 quotaValue를 뽑기 위한 정규식. */
    private static final Pattern QUOTA_VALUE_PATTERN =
            Pattern.compile("\"quotaValue\"\\s*:\\s*\"([^\"]+)\"");

    /** 429 응답에서 quotaMetric을 뽑기 위한 정규식. */
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
        log.info("[임베딩 taskType] document={}, query={}", documentTaskType, queryTaskType);
    }

    // ──────────────────────────────────────────────────────────────
    // 공개 API — 비대칭 임베딩(asymmetric embedding)
    //
    // 인덱싱과 검색은 서로 다른 taskType을 써야 한다.
    // 같은 taskType으로 인코딩하면 모델이 "형태가 비슷한 것끼리" 모으기 때문에
    // diff는 diff끼리, 코드는 코드끼리 뭉치고 정작 관련 있는 쌍은 멀어진다.
    //
    // 주의: taskType이 바뀌면 벡터 공간 자체가 바뀐다.
    //      기존 인덱스와 섞으면 유사도가 무의미해지므로 반드시 재인덱싱해야 한다.
    // ──────────────────────────────────────────────────────────────

    /**
     * 인덱싱(코퍼스)용 임베딩.
     * Pinecone에 저장할 코드 청크를 벡터로 변환할 때 사용한다.
     */
    public List<float[]> embedDocuments(List<String> texts) {
        return embedBatch(texts, documentTaskType);
    }

    /**
     * 검색 쿼리용 임베딩.
     * PR diff처럼 "답을 찾으러 온 질문"을 벡터로 변환할 때 사용한다.
     */
    public float[] embedQuery(String text) {
        return embedBatch(List.of(text), queryTaskType).get(0);
    }

    /**
     * 청크 텍스트 리스트의 총 토큰 수를 실측한다.
     * AST 청킹 전/후 비교 실험용.
     *
     * NOTE: 현재 구현은 청크를 하나로 합쳐서 한 번에 센다.
     *       실제 임베딩 API는 청크를 개별 요청으로 받으므로 측정 단위가 다르고,
     *       청크별 입력 상한 초과 여부도 이 방식으로는 알 수 없다.
     *       (별도 개선 과제)
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

        int idx = nextKeyIndex();
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

    // ──────────────────────────────────────────────────────────────
    // 내부 구현
    //
    // embedBatch를 private으로 둔 이유:
    // taskType 없이 호출할 수 있는 경로를 아예 남기지 않기 위해서다.
    // 호출자는 embedDocuments / embedQuery 중 하나를 반드시 골라야 하고,
    // 그 과정에서 "지금 인덱싱인가 검색인가"를 의식하게 된다.
    // ──────────────────────────────────────────────────────────────

    private List<float[]> embedBatch(List<String> texts, String taskType) {
        Map<String, Object> requestBody = buildRequestBody(texts, taskType);
        int totalAttempts = apiKeys.size() * 3;
        long delay = 1000;
        Exception lastException = null;

        for (int attempt = 1; attempt <= totalAttempts; attempt++) {
            try {
                return callEmbeddingApi(requestBody, taskType);
            } catch (WebClientResponseException e) {
                lastException = e;
                delay = handleApiException(e, attempt, totalAttempts, delay);
            } catch (Exception e) {
                lastException = e;
                delay = handleUnexpectedException(e, attempt, totalAttempts, delay);
            }
        }
        log.error("embedBatch 모든 재시도 소진 [{}회], taskType={}", totalAttempts, taskType);
        throw new IndexingException(IndexingErrorCode.EMBEDDING_EXHAUSTED, lastException);
    }

    private List<float[]> callEmbeddingApi(Map<String, Object> requestBody, String taskType) {
        long apiStart = System.currentTimeMillis();

        int idx = nextKeyIndex();
        String key = apiKeys.get(idx);

        log.info("[{}] Gemini embedding API 호출 시작. keyIndex={}, key={}, taskType={}",
                Thread.currentThread().getName(), idx, maskKey(key), taskType);

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

            log.info("[{}] Gemini embedding API 응답 수신. keyIndex={}, taskType={}, 소요={}ms",
                    Thread.currentThread().getName(), idx, taskType, System.currentTimeMillis() - apiStart);

            if (response == null || response.embeddings() == null) {
                throw new IndexingException(IndexingErrorCode.EMBEDDING_INVALID_REQUEST);
            }

            embedCallCount.incrementAndGet();

            return toFloatArrays(response.embeddings());
        } catch (WebClientResponseException e) {
            // 어느 키(프로젝트)에서 실패했는지 반드시 함께 기록
            log.warn("[{}] Gemini embedding API 실패. keyIndex={}, key={}, taskType={}, status={}",
                    Thread.currentThread().getName(), idx, maskKey(key), taskType, e.getStatusCode().value());
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
            // taskType이 이 엔드포인트에서 지원되지 않으면 여기로 떨어진다.
            // 설정값(gemini.embedding.query-task-type)을 RETRIEVAL_QUERY로 내려서 확인할 것.
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

    /**
     * AtomicInteger는 Integer.MAX_VALUE를 넘으면 음수로 감기므로
     * 단순 %를 쓰면 음수 인덱스가 되어 예외가 난다. floorMod로 항상 양수를 보장한다.
     */
    private int nextKeyIndex() {
        return Math.floorMod(keyIndex.getAndIncrement(), apiKeys.size());
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

    /**
     * batchEmbedContents는 requests 배열의 각 항목마다 taskType을 받는다.
     * (모델/컨텐츠와 같은 레벨에 놓아야 하며, config 하위가 아니다)
     */
    private Map<String, Object> buildRequestBody(List<String> texts, String taskType) {
        List<Map<String, Object>> requests = texts.stream()
                .map(text -> Map.<String, Object>of(
                        "model", EMBEDDING_MODEL,
                        "content", Map.of("parts", List.of(Map.of("text", text))),
                        "taskType", taskType
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