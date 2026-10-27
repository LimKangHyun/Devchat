package project.ai.rag;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/**
 * RAG 검색의 대체 처리(폴백)와 결과를 Prometheus로 내보낸다.
 * 단계가 실패해도 리뷰는 맥락을 줄인 채 정상 응답으로 나가서 에러로는 드러나지 않는다.
 * 그래서 대체 처리 횟수와 결과 종류를 따로 세어, 품질 저하를 지표로 알아챌 수 있게 한다.
 *
 * Prometheus 이름
 *  - rag_fallback_total{stage}    : 단계별 대체 처리 횟수
 *  - rag_context_total{result}    : 리뷰 파일별 검색 결과 (ok / empty / embedding_failed / error)
 *  - rag_search_seconds           : 리뷰 파일 1개의 검색 시간
 *  - rag_score_structural_top     : 구조 검색 후보 중 최고 코사인 유사도
 *  - rag_score_rerank_top / _fifth: 리랭커 1위·5위 점수
 * 하이브리드 검색 점수는 순위로만 계산한 RRF 점수라 품질을 나타내지 않아 수집하지 않는다.
 */
@Component
public class RagMetrics {

    /** 대체 처리가 일어난 단계. 태그 값은 지표 이름에 그대로 쓰이므로 고정된 값만 둔다. */
    public enum Stage {
        VECTOR_SEARCH("vector_search"),
        STRUCTURAL_SEARCH("structural_search"),
        METHOD_QUERY("method_query"),
        RERANK("rerank");

        private final String tag;
        Stage(String tag) { this.tag = tag; }
    }

    /** 리뷰 파일 하나의 검색 결과. */
    public enum Result {
        OK("ok"),
        EMPTY("empty"),
        EMBEDDING_FAILED("embedding_failed"),
        ERROR("error");

        private final String tag;
        Result(String tag) { this.tag = tag; }
    }

    private final MeterRegistry registry;
    private final Timer searchTimer;
    private final DistributionSummary structuralTopScore;
    private final DistributionSummary rerankTopScore;
    private final DistributionSummary rerankFifthScore;

    public RagMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.searchTimer = Timer.builder("rag.search")
            .description("리뷰 파일 1개의 RAG 검색 시간")
            .publishPercentiles(0.5, 0.9, 0.99)
            .register(registry);
        // 운영에는 정답이 없어 Recall을 잴 수 없으므로, 검색 결과의 점수 분포로 품질 변화를 본다.
        this.structuralTopScore = scoreSummary("rag.score.structural.top",
            "구조 검색 후보 중 가장 높은 코사인 유사도 (리뷰 파일 1개당 1회)");
        this.rerankTopScore = scoreSummary("rag.score.rerank.top",
            "리랭커 1위 점수 (리뷰 파일 1개당 1회)");
        this.rerankFifthScore = scoreSummary("rag.score.rerank.fifth",
            "리랭커 5위 점수. K=5 경계의 품질, 점수 기준으로 K를 정할 때 참고");
        // 한 번도 일어나지 않은 값도 0으로 보이도록 미리 등록한다 (없으면 Grafana에서 "No data"로 보인다).
        for (Stage s : Stage.values()) fallbackCounter(s);
        for (Result r : Result.values()) resultCounter(r);
    }

    public void fallback(Stage stage) {
        fallbackCounter(stage).increment();
    }

    public void result(Result result) {
        resultCounter(result).increment();
    }

    public Timer searchTimer() {
        return searchTimer;
    }

    /** 구조 검색 후보 중 최고 유사도. 후보가 없으면 기록하지 않는다(0으로 기록하면 분포가 왜곡된다). */
    public void structuralTopScore(double score) {
        structuralTopScore.record(score);
    }

    /** 리랭커 점수(높은 순). 1위와 5위를 기록한다. 5개 미만이면 5위는 기록하지 않는다. */
    public void rerankScores(java.util.List<Double> scoresDesc) {
        if (scoresDesc.isEmpty()) return;
        rerankTopScore.record(scoresDesc.get(0));
        if (scoresDesc.size() >= 5) rerankFifthScore.record(scoresDesc.get(4));
    }

    private DistributionSummary scoreSummary(String name, String description) {
        return DistributionSummary.builder(name)
            .description(description)
            .publishPercentiles(0.1, 0.5, 0.9)
            .register(registry);
    }

    private Counter fallbackCounter(Stage stage) {
        return Counter.builder("rag.fallback")
            .description("RAG 검색 단계가 실패해 낮은 품질로 대체 처리한 횟수")
            .tag("stage", stage.tag)
            .register(registry);
    }

    private Counter resultCounter(Result result) {
        return Counter.builder("rag.context")
            .description("리뷰 파일별 RAG 검색 결과")
            .tag("result", result.tag)
            .register(registry);
    }
}
