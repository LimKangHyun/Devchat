package project.backend.domain.aireview.app;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final WebClient.Builder webClientBuilder;

    @Value("${gemini.api-key}")
    private String apiKey;

    private static final String BATCH_EMBEDDING_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-embedding-001:batchEmbedContents";

    private final AtomicInteger embedCallCount = new AtomicInteger(0);

    public float[] embed(String text) {
        return embedBatch(List.of(text)).get(0);
    }

    public List<float[]> embedBatch(List<String> texts) {
        int maxRetry = 3;
        long delay = 1000;

        List<Map<String, Object>> requests = texts.stream()
                .map(text -> Map.<String, Object>of(
                        "model", "models/gemini-embedding-001",
                        "content", Map.of(
                                "parts", List.of(Map.of("text", text))
                        )
                ))
                .toList();

        Map<String, Object> requestBody = Map.of("requests", requests);

        for (int attempt = 1; attempt <= maxRetry; attempt++) {
            try {
                BatchEmbeddingResponse response = webClientBuilder
                        .codecs(configurer -> configurer
                                .defaultCodecs()
                                .maxInMemorySize(10 * 1024 * 1024))
                        .build()
                        .post()
                        .uri(BATCH_EMBEDDING_URL + "?key=" + apiKey)
                        .header("Content-Type", "application/json")
                        .bodyValue(requestBody)
                        .retrieve()
                        .bodyToMono(BatchEmbeddingResponse.class)
                        .block();

                if (response == null || response.embeddings() == null) {
                    throw new IllegalStateException("Gemini 배치 임베딩 응답 없음");
                }

                embedCallCount.incrementAndGet();

                List<float[]> result = response.embeddings().stream()
                        .map(embedding -> {
                            List<Float> values = embedding.values();
                            float[] arr = new float[values.size()];
                            for (int i = 0; i < values.size(); i++) arr[i] = values.get(i);
                            return arr;
                        })
                        .toList();

                Thread.sleep(100);

                return result;

            } catch (Exception e) {
                log.warn("embedBatch 실패 (시도 {}/{}): {}", attempt, maxRetry, e.getMessage());
                if (attempt == maxRetry) throw new RuntimeException("embedBatch 최대 재시도 초과", e);
                try {
                    Thread.sleep(delay);
                    delay *= 2;
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("embedBatch 재시도 중단", ie);
                }
            }
        }
        throw new IllegalStateException("embedBatch 실패");
    }

    private record BatchEmbeddingResponse(List<Embedding> embeddings) {
        record Embedding(List<Float> values) {}
    }

    public int getAndResetCount() {
        return embedCallCount.getAndSet(0);
    }
}