package project.ai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class CohereRerankService {

    private final WebClient webClient;

    @Value("${cohere.api-key}")
    private String apiKey;

    private static final String RERANK_URL = "https://api.cohere.com/v2/rerank";
    private static final String MODEL = "rerank-v3.5";

    public CohereRerankService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    /**
     * Cohere Rerank API를 호출하여 query 대비 documents의 관련도를 재정렬한다.
     * 반환값은 원본 documents 리스트 기준의 index와 relevance score.
     */
    public List<RerankResult> rerank(String query, List<String> documents, int topN) {
        if (documents.isEmpty()) return List.of();

        Map<String, Object> requestBody = Map.of(
            "model", MODEL,
            "query", query,
            "documents", documents,
            "top_n", topN
        );

        try {
            RerankResponse response = webClient.post()
                .uri(RERANK_URL)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(RerankResponse.class)
                .block();

            if (response == null || response.results() == null) {
                log.warn("[Rerank] 응답 없음, 원본 순서 유지");
                return List.of();
            }

            log.info("[Rerank] 완료. 입력={}개, 반환={}개", documents.size(), response.results().size());
            return response.results();

        } catch (Exception e) {
            log.warn("[Rerank] API 호출 실패, 원본 순서 유지. error={}", e.getMessage());
            return List.of();
        }
    }

    public record RerankResponse(List<RerankResult> results) {}

    public record RerankResult(int index, double relevanceScore) {}
}