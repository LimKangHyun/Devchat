package project.ai.client;

import java.time.Duration;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import project.ai.indexing.SparseEncoder.SparseVector;
import project.ai.rag.SearchHit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

/**
 * Qdrant REST API 클라이언트.
 *
 * 컬렉션 하나에 모든 저장소의 청크를 넣고, payload의 repoId로 저장소를 구분한다.
 * 그래서 모든 조회·삭제는 repoId 조건을 반드시 함께 건다.
 */
@Slf4j
@Component
public class QdrantClient {

    /** gemini-embedding-001 기본 출력 차원. 바꾸면 컬렉션을 새로 만들고 전체 재인덱싱해야 한다. */
    private static final int VECTOR_SIZE = 3072;

    /** 포인트 하나에 dense(의미)와 sparse(BM25 키워드) 벡터를 이름으로 구분해 함께 저장한다. */
    private static final String DENSE_VECTOR = "dense";
    private static final String SPARSE_VECTOR = "bm25";

    /** 필터에 쓰는 payload 필드. 인덱스가 없으면 필터 검색이 전체 스캔이 된다. */
    private static final List<String> KEYWORD_INDEX_FIELDS = List.of(
            "filePath", "className", "methodName", "superClassName", "interfaceNames",
            "calledMethodNames", "calledMethodQualified", "referencedTypeNames"
    );

    private static final int SCROLL_PAGE_SIZE = 256;

    private final WebClient.Builder webClientBuilder;
    private WebClient webClient;

    @Value("${qdrant.url}")
    private String url;

    @Value("${qdrant.api-key}")
    private String apiKey;

    @Value("${qdrant.collection:devchat}")
    private String collection;

    /**
     * Qdrant 동시 호출 상한.
     * 스레드 풀 크기와 별개로 두는 이유: 병목은 스레드가 아니라 Qdrant다.
     * 구조 검색(요청당 최대 ~20쿼리), 벡터 검색, 인덱싱 upsert가 모두 같은 Qdrant를
     * 쓰므로 호출 지점마다 제한하지 않고 클라이언트에서 한 번에 막는다.
     * 가상 스레드 환경에서는 큐/거부 정책이 없어 이 세마포어가 유일한 제동장치가 된다.
     */
    @Value("${qdrant.max-concurrent-calls:30}")
    private int maxConcurrentCalls;

    /** 검색 요청 응답 제한. 리뷰 요청 경로라 짧게 둔다. 넘기면 예외가 나고 호출한 쪽의 대체 처리를 탄다. */
    @Value("${qdrant.search-timeout:5s}")
    private Duration searchTimeout;

    /** 저장·삭제·컬렉션 관리 요청 응답 제한. 인덱싱 배치라 길게 둔다. */
    @Value("${qdrant.write-timeout:60s}")
    private Duration writeTimeout;

    private Semaphore semaphore;

    public QdrantClient(WebClient.Builder webClientBuilder) {
        this.webClientBuilder = webClientBuilder;
    }

    /** 검색 필터 조건. 레코드라 toString()으로 같은 조건인지 비교할 수 있다. */
    public sealed interface Condition permits Match, Nested {}

    /**
     * 필드가 value와 같거나, 배열 필드가 value를 포함하면 매칭.
     * value는 String 또는 Boolean.
     */
    public record Match(String key, Object value) implements Condition {}

    /**
     * 객체 배열 필드(key)의 원소 중 하나가 must를 "동시에" 만족하면 매칭.
     * 평면 필드 조건을 여러 개 걸면 서로 다른 원소의 값이 섞여 매칭될 수 있는데,
     * nested는 같은 원소 안에서만 조건을 비교한다.
     */
    public record Nested(String key, List<Match> must) implements Condition {}

    /** 저장할 포인트. id는 UUID 문자열이어야 한다. sparse가 비어 있으면 dense만 저장한다. */
    public record Point(String id, float[] vector, SparseVector sparse, Map<String, Object> payload) {}

    @PostConstruct
    public void init() {
        this.webClient = webClientBuilder.clone()
                .baseUrl(url)
                .defaultHeader("api-key", apiKey)
                .codecs(c -> c.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();
        this.semaphore = new Semaphore(maxConcurrentCalls);

        // 시작 시 연결에 실패해도 서버는 띄운다. 검색 실패는 RAG 쪽에서 "컨텍스트 없음"으로 처리된다.
        try {
            ensureCollection();
            log.info("Qdrant 연결 완료. collection={}, maxConcurrentCalls={}", collection, maxConcurrentCalls);
        } catch (Exception e) {
            log.error("Qdrant 컬렉션 준비 실패. collection={}", collection, e);
        }
    }

    // ── 저장 ──

    public void upsert(List<Point> points) {
        if (points.isEmpty()) return;

        List<Map<String, Object>> body = points.stream()
                .map(p -> Map.<String, Object>of("id", p.id(), "vector", vectors(p), "payload", p.payload()))
                .toList();

        withLimit(() -> webClient.put()
                .uri("/collections/{c}/points?wait=true", collection)
                .bodyValue(Map.of("points", body))
                .retrieve()
                .toBodilessEntity()
                .block(writeTimeout));
        log.debug("Qdrant upsert. count={}", points.size());
    }

    private Map<String, Object> vectors(Point p) {
        if (p.sparse() == null || p.sparse().isEmpty()) {
            return Map.of(DENSE_VECTOR, p.vector());
        }
        return Map.of(DENSE_VECTOR, p.vector(), SPARSE_VECTOR, sparseJson(p.sparse()));
    }

    private Map<String, Object> sparseJson(SparseVector sparse) {
        return Map.of("indices", sparse.indices(), "values", sparse.values());
    }

    // ── 검색 ──

    /** 필터 없는 유사도 검색. 결과는 유사도 높은 순. */
    public List<SearchHit> search(String repoId, float[] vector, int limit) {
        return search(repoId, vector, limit, List.of());
    }

    /** must 조건을 모두 만족하는 것 중 유사도 높은 순. */
    public List<SearchHit> search(String repoId, float[] vector, int limit, List<? extends Condition> must) {
        return query(Map.of(
                "query", vector,
                "using", DENSE_VECTOR,
                "limit", limit,
                "filter", repoFilter(repoId, must),
                "with_payload", true
        ));
    }

    /**
     * dense(의미)와 sparse(키워드) 검색을 따로 돌려 RRF로 합친 하이브리드 검색.
     * RRF는 점수 크기가 아니라 순위만 쓰므로, 척도가 다른 코사인 유사도와 BM25 점수를 그대로 합칠 수 있다.
     * 결과의 score는 RRF 점수라 유사도로 해석하면 안 된다.
     *
     * @param prefetchLimit 각 검색에서 가져올 후보 수. limit보다 커야 한쪽에만 걸린 후보도 합산된다.
     */
    public List<SearchHit> hybridSearch(String repoId, float[] dense, SparseVector sparse,
                                        int limit, int prefetchLimit) {
        if (sparse == null || sparse.isEmpty()) return search(repoId, dense, limit);

        Map<String, Object> filter = repoFilter(repoId, List.of());
        return query(Map.of(
                "prefetch", List.of(
                        Map.of("query", dense, "using", DENSE_VECTOR, "limit", prefetchLimit, "filter", filter),
                        Map.of("query", sparseJson(sparse), "using", SPARSE_VECTOR, "limit", prefetchLimit,
                                "filter", filter)),
                "query", Map.of("fusion", "rrf"),
                "limit", limit,
                "with_payload", true
        ));
    }

    private List<SearchHit> query(Map<String, Object> body) {
        QueryResponse response = withLimit(() -> webClient.post()
                .uri("/collections/{c}/points/query", collection)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(QueryResponse.class)
                .block(searchTimeout));

        if (response == null || response.result() == null || response.result().points() == null) {
            return List.of();
        }
        return response.result().points().stream()
                .map(p -> new SearchHit(String.valueOf(p.id()), p.score(),
                        p.payload() != null ? p.payload() : Map.of()))
                .toList();
    }

    // ── 조회·삭제 ──

    /** 파일에 속한 포인트 ID 목록. */
    public List<String> listFileChunkIds(String repoId, String filePath) {
        Map<String, Object> filter = repoFilter(repoId, List.of(new Match("filePath", filePath)));
        List<String> ids = new ArrayList<>();
        Object offset = null;

        do {
            Map<String, Object> body = new HashMap<>();
            body.put("filter", filter);
            body.put("limit", SCROLL_PAGE_SIZE);
            body.put("with_payload", false);
            body.put("with_vector", false);
            if (offset != null) body.put("offset", offset);

            ScrollResponse response = withLimit(() -> webClient.post()
                    .uri("/collections/{c}/points/scroll", collection)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(ScrollResponse.class)
                    .block(writeTimeout));

            if (response == null || response.result() == null) break;
            response.result().points().forEach(p -> ids.add(String.valueOf(p.id())));
            offset = response.result().nextPageOffset();
        } while (offset != null);

        return ids;
    }

    public void deleteByIds(List<String> ids) {
        if (ids.isEmpty()) return;
        delete(Map.of("points", ids));
    }

    /** 파일에 속한 청크 전부 삭제. */
    public void deleteFileChunks(String repoId, String filePath) {
        delete(Map.of("filter", repoFilter(repoId, List.of(new Match("filePath", filePath)))));
        log.info("Qdrant 파일 청크 삭제. repoId={}, filePath={}", repoId, filePath);
    }

    /** 저장소의 청크 전부 삭제. */
    public void deleteRepo(String repoId) {
        delete(Map.of("filter", repoFilter(repoId, List.of())));
        log.info("Qdrant 저장소 청크 삭제. repoId={}", repoId);
    }

    private void delete(Map<String, Object> body) {
        withLimit(() -> webClient.post()
                .uri("/collections/{c}/points/delete?wait=true", collection)
                .bodyValue(body)
                .retrieve()
                .toBodilessEntity()
                .block(writeTimeout));
    }

    // ── 컬렉션 준비 ──

    private void ensureCollection() {
        ExistsResponse exists = webClient.get()
                .uri("/collections/{c}/exists", collection)
                .retrieve()
                .bodyToMono(ExistsResponse.class)
                .block(writeTimeout);

        if (exists == null || exists.result() == null || !exists.result().exists()) {
            // sparse 벡터의 IDF는 modifier: idf로 Qdrant가 컬렉션 통계로 계산한다(SparseEncoder는 TF만 계산).
            webClient.put()
                    .uri("/collections/{c}", collection)
                    .bodyValue(Map.of(
                            "vectors", Map.of(DENSE_VECTOR, Map.of("size", VECTOR_SIZE, "distance", "Cosine")),
                            "sparse_vectors", Map.of(SPARSE_VECTOR, Map.of("modifier", "idf"))))
                    .retrieve()
                    .toBodilessEntity()
                    .block(writeTimeout);
            log.info("Qdrant 컬렉션 생성. collection={}, size={}", collection, VECTOR_SIZE);
        } else {
            warnIfLegacySchema();
        }

        // 이미 있는 인덱스를 다시 만들어도 Qdrant는 그대로 둔다. 필드를 추가했을 때 반영되도록 매번 호출한다.
        // repoId는 모든 요청에 걸리는 저장소 구분 키라 tenant로 지정해 저장소별로 데이터를 모아 둔다.
        createIndex("repoId", Map.of("type", "keyword", "is_tenant", true));
        KEYWORD_INDEX_FIELDS.forEach(field -> createIndex(field, "keyword"));

        // nested 필터가 비교하는 객체 배열 안의 필드. "배열[].필드" 형식으로 인덱싱한다.
        createIndex("calledMethodRefs[].methodName", "keyword");
        createIndex("calledMethodRefs[].targetClassHint", "keyword");
        createIndex("calledMethodRefs[].resolved", "bool");
    }

    /**
     * 이름 없는 단일 벡터로 만든 예전 컬렉션은 dense/bm25 이름으로 저장·검색할 수 없다.
     * 컬렉션을 자동으로 지우면 데이터가 날아가므로, 새 컬렉션 이름으로 전체 재인덱싱하라고 알리기만 한다.
     */
    private void warnIfLegacySchema() {
        CollectionInfoResponse info = webClient.get()
                .uri("/collections/{c}", collection)
                .retrieve()
                .bodyToMono(CollectionInfoResponse.class)
                .block(writeTimeout);
        Object vectors = info == null || info.result() == null ? null
                : info.result().config().params().vectors();
        if (!(vectors instanceof Map<?, ?> named) || !named.containsKey(DENSE_VECTOR)) {
            log.error("Qdrant 컬렉션이 예전 스키마(이름 없는 벡터)입니다. dense/bm25 하이브리드 스키마로 "
                    + "새 컬렉션을 만들어 전체 재인덱싱해야 합니다. collection={}", collection);
        }
    }

    private void createIndex(String field, Object schema) {
        webClient.put()
                .uri("/collections/{c}/index?wait=true", collection)
                .bodyValue(Map.of("field_name", field, "field_schema", schema))
                .retrieve()
                .toBodilessEntity()
                .block(writeTimeout);
    }

    // ── 공통 ──

    private Map<String, Object> repoFilter(String repoId, List<? extends Condition> must) {
        List<Map<String, Object>> conditions = new ArrayList<>(must.size() + 1);
        conditions.add(toJson(new Match("repoId", repoId)));
        must.forEach(c -> conditions.add(toJson(c)));
        return Map.of("must", conditions);
    }

    private Map<String, Object> toJson(Condition condition) {
        return switch (condition) {
            case Match m -> Map.of("key", m.key(), "match", Map.of("value", m.value()));
            case Nested n -> Map.of("nested", Map.of(
                    "key", n.key(),
                    "filter", Map.of("must", n.must().stream().map(this::toJson).toList())));
        };
    }

    /**
     * 세마포어로 동시 호출 수를 제한하며 Qdrant 호출을 실행한다.
     * 가상 스레드에서 블록되면 캐리어 스레드를 점유하지 않으므로 대기 비용이 낮다.
     */
    private <T> T withLimit(Supplier<T> call) {
        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Qdrant 호출 대기 중 인터럽트", e);
        }
        try {
            return call.get();
        } finally {
            semaphore.release();
        }
    }

    // ── 응답 ──

    record QueryResponse(QueryResult result) {}

    record QueryResult(List<ScoredPoint> points) {}

    record ScoredPoint(Object id, float score, Map<String, Object> payload) {}

    record ScrollResponse(ScrollResult result) {}

    record ScrollResult(
            List<PointId> points,
            @JsonProperty("next_page_offset") Object nextPageOffset) {}

    record PointId(Object id) {}

    record CollectionInfoResponse(CollectionInfo result) {}

    record CollectionInfo(CollectionConfig config) {}

    record CollectionConfig(CollectionParams params) {}

    /** vectors는 이름 없는 단일 벡터면 {size, distance}, 이름 있는 벡터면 {이름: {size, distance}}다. */
    record CollectionParams(Object vectors) {}

    record ExistsResponse(Exists result) {}

    record Exists(boolean exists) {}
}
