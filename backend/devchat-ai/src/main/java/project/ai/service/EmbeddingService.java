package project.ai.service;

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

    public float[] embed(String text) {
        return embedBatch(List.of(text)).get(0);
    }

    public List<float[]> embedBatch(List<String> texts) {
        Map<String, Object> requestBody = buildRequestBody(texts);
        int totalAttempts = apiKeys.size() * 3;
        long delay = 1000;

        for (int attempt = 1; attempt <= totalAttempts; attempt++) {
            try {
                return callEmbeddingApi(requestBody);
            } catch (WebClientResponseException e) {
                delay = handleApiException(e, attempt, totalAttempts, delay);
            } catch (Exception e) {
                delay = handleUnexpectedException(e, attempt, totalAttempts, delay);
            }
        }
        throw new IndexingException(IndexingErrorCode.EMBEDDING_EXHAUSTED);
    }

    private List<float[]> callEmbeddingApi(Map<String, Object> requestBody) {
        BatchEmbeddingResponse response = webClientBuilder
                .codecs(c -> c.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build()
                .post()
                .uri(BATCH_EMBEDDING_URL + "?key=" + nextKey())
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(BatchEmbeddingResponse.class)
                .block();

        if (response == null || response.embeddings() == null) {
            throw new IndexingException(IndexingErrorCode.EMBEDDING_INVALID_REQUEST);
        }

        embedCallCount.incrementAndGet();
        sleep(100);
        return toFloatArrays(response.embeddings());
    }

    private long handleApiException(WebClientResponseException e, int attempt, int totalAttempts, long delay) {
        int status = e.getStatusCode().value();

        if (status == 400 || status == 401 || status == 403) {
            throw new IndexingException(IndexingErrorCode.EMBEDDING_FAILED);
        }
        if (status == 429) {
            log.warn("embedBatch 429 - 키 로테이션 [시도 {}/{}]", attempt, totalAttempts);
            return delay; // 백오프 없이 즉시 다음 키
        }

        log.warn("embedBatch 5xx 오류 [시도 {}/{}]: status={}", attempt, totalAttempts, status);
        throwIfExhausted(e, attempt, totalAttempts);
        backoff(delay);
        return delay * 2;
    }

    private long handleUnexpectedException(Exception e, int attempt, int totalAttempts, long delay) {
        log.warn("embedBatch 예외 [시도 {}/{}]: {}", attempt, totalAttempts, e.getMessage());
        throwIfExhausted(e, attempt, totalAttempts);
        backoff(delay);
        return delay * 2;
    }

    private void throwIfExhausted(Exception e, int attempt, int totalAttempts) {
        if (attempt == totalAttempts) {
            throw new IndexingException(IndexingErrorCode.EMBEDDING_FAILED);
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

    private record BatchEmbeddingResponse(List<Embedding> embeddings) {
        record Embedding(List<Float> values) {}
    }

    public int getAndResetCount() {
        return embedCallCount.getAndSet(0);
    }
}