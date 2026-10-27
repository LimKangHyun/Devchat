package project.ai.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class CohereClient {

    private final WebClient webClient;

    @Value("${cohere.api-key}")
    private String apiKey;

    /** 리랭크 응답 제한. 넘기면 검색 순서대로 대체한다. */
    @Value("${cohere.timeout:10s}")
    private Duration timeout;

    private static final String RERANK_URL = "https://api.cohere.com/v2/rerank";
    private static final String MODEL = "rerank-v3.5";

    public CohereClient(WebClient.Builder webClientBuilder) {
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

        long start = System.currentTimeMillis();
        try {
            RerankResponse response = webClient.post()
                .uri(RERANK_URL)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(RerankResponse.class)
                .block(timeout);

            if (response == null || response.results() == null) {
                log.warn("[Rerank] 응답 없음, 원본 순서 유지. 소요={}ms", System.currentTimeMillis() - start);
                return List.of();
            }

            // 튜닝용: 결과는 점수 높은 순이다. 5위와 6위의 차이가 작으면 K 경계가 불안정하다는 뜻이다.
            List<RerankResult> results = response.results();
            log.info("[Rerank] 완료. 입력={}개, 반환={}개, 소요={}ms, top1={}, top5={}, gap5_6={}",
                documents.size(), results.size(), System.currentTimeMillis() - start,
                scoreAt(results, 0), scoreAt(results, 4), gap(results, 4, 5));
            return results;

        } catch (WebClientResponseException e) {
            log.warn("[Rerank] API 오류, 원본 순서 유지. status={}, 소요={}ms, body={}",
                e.getStatusCode().value(), System.currentTimeMillis() - start, e.getResponseBodyAsString());
            return List.of();
        } catch (IllegalStateException e) {
            // Mono.block(timeout)은 시간을 넘기면 IllegalStateException을 던진다.
            log.warn("[Rerank] 응답 시간 초과({}), 원본 순서 유지", timeout);
            return List.of();
        } catch (Exception e) {
            log.warn("[Rerank] API 호출 실패, 원본 순서 유지. 소요={}ms, error={}",
                System.currentTimeMillis() - start, e.toString());
            return List.of();
        }
    }

    private static String scoreAt(List<RerankResult> results, int i) {
        return i < results.size() ? String.format("%.3f", results.get(i).relevanceScore()) : "-";
    }

    private static String gap(List<RerankResult> results, int a, int b) {
        return b < results.size()
            ? String.format("%.3f", results.get(a).relevanceScore() - results.get(b).relevanceScore()) : "-";
    }

    public record RerankResponse(List<RerankResult> results) {}

    public record RerankResult(int index, double relevanceScore) {}
}