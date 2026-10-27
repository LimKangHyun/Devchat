package project.ai.rag;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class RagMetricsTest {

    @Test
    void 대체_처리와_검색_결과가_Prometheus_형식으로_노출된다() {
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        RagMetrics metrics = new RagMetrics(registry);

        metrics.fallback(RagMetrics.Stage.RERANK);
        metrics.fallback(RagMetrics.Stage.RERANK);
        metrics.result(RagMetrics.Result.EMBEDDING_FAILED);
        metrics.searchTimer().record(1200, TimeUnit.MILLISECONDS);
        metrics.structuralTopScore(0.82);
        metrics.rerankScores(java.util.List.of(0.91, 0.7, 0.5, 0.4, 0.33, 0.2));
        metrics.rerankScores(java.util.List.of(0.6, 0.5)); // 5개 미만이면 5위는 기록하지 않는다

        String scrape = registry.scrape();
        assertThat(scrape).contains("rag_fallback_total{stage=\"rerank\"} 2.0");
        // 한 번도 일어나지 않은 단계도 0으로 보여야 Grafana에서 "No data" 대신 0이 나온다.
        assertThat(scrape).contains("rag_fallback_total{stage=\"structural_search\"} 0.0");
        assertThat(scrape).contains("rag_context_total{result=\"embedding_failed\"} 1.0");
        assertThat(scrape).contains("rag_search_seconds_count 1");
        assertThat(scrape).contains("rag_score_structural_top_count 1");
        assertThat(scrape).contains("rag_score_rerank_top_count 2");
        assertThat(scrape).contains("rag_score_rerank_fifth_count 1");
        assertThat(scrape).contains("rag_score_rerank_top{quantile=\"0.5\"}");
    }
}
