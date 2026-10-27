package project.ai.rag;

import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import project.ai.indexing.chunking.ChunkMeta.CalledMethodRef;
import project.ai.indexing.structural.ChangedSymbol;
import project.ai.indexing.structural.MethodNameFilter;
import project.ai.indexing.structural.ProjectTypeFilter;
import project.ai.client.QdrantClient;
import project.ai.client.QdrantClient.Condition;
import project.ai.client.QdrantClient.Match;
import project.ai.client.QdrantClient.Nested;
import project.ai.indexing.structural.StructuralInfo;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;


/**
 * 구조적 관계(구현/상속/호출/참조) 기반 검색을 담당한다.
 *
 * 메타데이터 필터를 OR로 묶으면 "어느 조건으로 매칭됐는지"를 알 수 없다.
 * 그 정보가 곧 관계 태그이고 우선순위 정렬의 기준이므로, 관계별로 쿼리를 나누는 것은
 * 설계상 불가피하다. 대신 두 가지로 비용을 통제한다.
 *  1) 결과가 나올 수 없는 쿼리(JDK/외부 타입)는 만들기 전에 거른다.
 *  2) 남은 쿼리는 서로 독립적이므로 병렬 실행한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StructuralSearchService {

    private final QdrantClient qdrantClient;
    private final RagMetrics ragMetrics;

    @Qualifier("structuralSearchExecutor")
    private final ExecutorService structuralSearchExecutor;

    /**
     * 추정 호출자(nested) 쿼리 사용 여부. 끄면 이름만 매칭하는 이전 방식과 같아진다.
     * 검색 품질 평가에서 두 방식을 비교하려고 둔 스위치다.
     */
    @Value("${rag.structural.guessed-caller-search:true}")
    private boolean guessedCallerSearchEnabled;

    /**
     * diff에서 추가된 줄의 호출(새 코드가 의존하는 메서드)을 먼저 조회할지.
     * 바뀐 메서드 본문 전체의 호출을 모으는 기존 사용호출 검색은 개수 제한에 걸려 새 호출이 잘렸다.
     * 평가(파일 단위 Recall@5): 32.9% → 44.8%.
     */
    @Value("${rag.structural.added-call-search:true}")
    private boolean addedCallSearchEnabled;

    /**
     * 대상 클래스가 확정된 추가 줄 호출을 확정 슬롯(rerank 우회) 후보로 둘지.
     * 새 코드가 직접 부르는 메서드라 관계가 확실하다. 평가: 44.8% → 50.0%, Precision@5도 올랐다.
     */
    @Value("${rag.structural.added-call-slot:true}")
    private boolean addedCallSlotEnabled;

    /**
     * 새 코드가 부르는 메서드라도 getter·인터페이스 선언처럼 볼 내용이 없는 단순 메서드는 확정 슬롯에서 뺄지.
     * 후보로는 남아 리랭커가 판단한다.
     */
    @Value("${rag.structural.added-call-slot-skip-trivial:false}")
    private boolean addedCallSlotSkipTrivial;

    /**
     * 새 코드가 부르는 단순 메서드(getter·인터페이스 선언)에 낮은 우선순위 태그를 붙일지. 슬롯에서도 자연히 빠진다.
     * 평가(파일 단위): Recall@5 50.0% → 50.5%, Precision@5 30.8% → 31.8%, 잃은 정답 없음.
     */
    @Value("${rag.structural.added-call-demote-trivial:true}")
    private boolean addedCallDemoteTrivial;

    private static final String ADDED_CALL_TRIVIAL_TAG = "사용호출(추가줄,단순):";
    private final TrivialMethodDetector trivialMethodDetector;

    private static final String ADDED_CALL_TAG = "사용호출(추가줄):";

    private static final int METADATA_CANDIDATE_SIZE = 10;

    /**
     * 호출자 쿼리의 결과 상한. 변경된 메서드의 호출부는 하나하나가 직접 영향 지점이라
     * 10개에서 자르면 나머지를 통째로 놓친다(정적 유틸 호출부가 20곳인 경우 등).
     * 다른 관계(사용타입·형제 등)는 맥락용이라 기본 상한을 유지한다.
     */
    private static final int CALLER_CANDIDATE_SIZE = 30;
    private static final int MAX_INTERFACE_QUERIES = 3;
    private static final int MAX_REFERENCED_TYPE_QUERIES = 5;
    private static final int MAX_METHOD_QUERIES = 3;
    // get으로 시작하는 유틸 호출도 쿼리에 포함하면서 대상이 늘어나 5에서 올렸다.
    private static final int MAX_CALLED_REF_QUERIES = 8;
    /**
     * 추가된 줄의 호출 쿼리 상한. 평소엔 걸리지 않을 값으로, 큰 파일을 새로 추가한 PR에서
     * Qdrant 요청이 한꺼번에 몰리는 것만 막는다. 평가 데이터에서 파일당 최대 22개(새 파일), 중앙값 1개.
     */
    private static final int MAX_ADDED_CALL_QUERIES = 30;
    private static final int MAX_REMOVED_SYMBOL_QUERIES = 5;
    /** 삭제된 줄의 호출 쿼리 상한. 추가된 줄과 같은 이유(큰 코드 삭제 시 요청 폭주 방지). */
    private static final int MAX_REMOVED_CALL_QUERIES = 30;

    private static final String REMOVED_CALL_TAG = "사용호출(삭제줄):";

    /** diff에서 삭제된 줄의 호출도 조회할지. */
    @Value("${rag.structural.removed-call-search:false}")
    private boolean removedCallSearchEnabled;

    /**
     * 인터페이스를 거친 관계도 조회할지.
     *  - 바뀐 메서드를 그 클래스가 구현한 인터페이스(또는 부모 클래스) 타입으로 부르는 호출자
     *  - 새 코드·바뀐 메서드가 부르는 인터페이스 메서드의 구현체
     * 코드에는 인터페이스 타입만 보여서 기존 쿼리로는 실제 구현과 이어지지 않았다.
     */
    @Value("${rag.structural.interface-relation-search:true}")
    private boolean interfaceRelationSearchEnabled;

    /** 위 두 관계 중 호출자 쪽만 따로 끈다. 인터페이스 타입 호출자는 너무 많아 노이즈가 컸다. */
    @Value("${rag.structural.interface-caller-search:false}")
    private boolean interfaceCallerSearchEnabled;

    private static final String INTERFACE_CALLER_TAG = "호출자(인터페이스):";
    private static final String CALLED_IMPL_TAG = "구현체(호출대상):";
    private static final int MAX_CALLED_IMPL_QUERIES = 5;
    /** 구현체가 이보다 많으면(AuthenticationConverter처럼 두루 쓰는 인터페이스) 어느 구현이 불릴지 몰라 버린다. */
    private static final int MAX_CALLED_IMPLEMENTERS = 6;

    // 변경으로 "깨질 수 있는" 쪽(변경된 파일을 쓰는 코드)을 먼저 가져온다.
    // 인터페이스/부모 시그니처가 바뀌면 구현체·상속체가 컴파일 에러가 나므로 리뷰에 필수.
    // 반대로 "변경 코드가 쓰는 타입/메서드"는 이 PR에서 안 바뀌므로 깨지지 않는다(맥락용).
    // "호출자(삭제됨):"이 최상위인 이유: 사라진 메서드를 호출하는 코드는 추정이 아니라
    // 컴파일 에러가 확정이다.
    // 확정/추정 구분: 대상 클래스를 선언 타입에서 읽은 것(사용호출)이 이름 관례로 추정한
    // 것(사용호출(추정))보다 신뢰도가 높다. 호출자도 같은 기준으로
    // 호출자(확정) > 호출자(추정, 클래스까지 일치) > 호출자(이름만) 순이다.
    // 주의: 이 문자열은 buildFilterQueries()/buildFileWideQueries()가 붙이는 태그와
    //       startsWith로 매칭되므로, 태그 문구를 수정하면 여기와
    //       STRONG_RELATION_TAGS도 함께 고쳐야 한다.
    private static final List<String> PRIORITY_ORDER = List.of(
        "호출자(삭제됨):", "구현체:", "자식클래스:", "호출자:", "사용호출(추가줄):", "사용호출(삭제줄):",
        "호출자(삭제됨,추정):", "호출자(추정):", "호출자(인터페이스):", "구현체(호출대상):",
        "사용호출(추가줄,추정):", "사용호출(삭제줄,추정):", "참조자:",
        "호출자(이름만):", "호출자(파일전체):", "호출자(파일전체,추정):",
        "형제(구현):", "형제(상속):", "사용타입:", "사용호출(추가줄,단순):", "사용호출:", "사용호출(추정):", "사용호출(이름만):"
    );

    /**
     * 확정 슬롯(rerank 우회)에 넣을 수 있는 관계.
     * 구현체/자식클래스는 AST 구조상 확정이고, "호출자:"는 calledMethodQualified
     * (클래스명까지 확정된 호출)로 매칭된 결과라 대상이 특정된다.
     * "호출자(삭제됨):"은 리네이밍/삭제된 메서드의 호출부라 컴파일이 확실히 깨진다.
     * "호출자(파일전체):"는 변경 심볼을 못 구했을 때 파일의 임의 메서드 기준으로 만든
     * 쿼리라, 관계는 정확해도 PR과 무관할 수 있어 제외한다.
     * "(추정)"이 붙은 호출자는 대상 클래스를 변수 이름으로 추정한 호출이라, 클래스까지
     * 일치해도 틀릴 수 있으므로 제외하고 rerank가 판단하게 한다.
     * 본문만 바뀐 메서드의 호출자도 슬롯에 넣는다. build()·configure()처럼 두루 쓰는 메서드면
     * 무관한 호출부가 섞이지만, 평가에서 슬롯을 rerank로 돌리자 그 자리를 더 부정확한
     * 벡터 후보가 채워 Recall·Precision이 모두 떨어졌다.
     */
    private static final Set<String> STRONG_RELATION_TAGS = Set.of(
        "호출자(삭제됨):", "구현체:", "자식클래스:", "호출자:"
    );

    private boolean isStrongRelation(String tag) {
        // "호출자(이름만):"이 "호출자:"로 시작하지 않도록 접두어 전체로 판별한다.
        // PRIORITY_ORDER 항목은 콜론까지 포함하므로 startsWith로 구분된다.
        for (String strong : STRONG_RELATION_TAGS) {
            if (tag.startsWith(strong)) return true;
        }
        return addedCallSlotEnabled && tag.startsWith(ADDED_CALL_TAG);
    }

    public record StructuralSearchResult(
        List<SearchHit> candidates,
        Map<String, String> relationshipTags
    ) {
        public static final StructuralSearchResult EMPTY =
            new StructuralSearchResult(List.of(), Map.of());
    }

    /** @param must 모두 만족해야 하는 조건. 레코드 리스트라 toString()으로 같은 필터인지 비교할 수 있다. */
    private record FilterQuery(List<Condition> must, String relationshipTag) {}

    private record FilterQueryResult(
        String relationshipTag,
        List<SearchHit> hits
    ) {}

    /**
     * @param removedSymbols 변경 전에는 있었으나 변경 후 사라진 메서드 이름
     *                       (리네이밍된 옛 이름 또는 삭제된 메서드)
     * @param addedCalls     diff에서 추가된 줄의 호출 (대상 클래스 힌트가 있는 것만)
     * @param removedCalls   diff에서 삭제된 줄의 호출 (변경 전 파일 기준, 대상 클래스 힌트가 있는 것만)
     */
    public StructuralSearchResult search(
        float[] vector, String namespace, String filePath, StructuralInfo info,
        List<ChangedSymbol> changedSymbols, Set<String> removedSymbols, List<CalledMethodRef> addedCalls,
        List<CalledMethodRef> removedCalls) {

        try {
            if (info.isEmpty()) return StructuralSearchResult.EMPTY;

            List<FilterQuery> queries = new ArrayList<>();
            // 같은 필터가 겹치면 먼저 들어간 태그가 남으므로, 기존 사용호출보다 앞에 넣는다.
            // 옮겨진 호출(삭제 후 다시 추가)은 추가줄 태그로 남도록 추가줄을 먼저 넣는다.
            if (addedCallSearchEnabled) addAddedCallQueries(queries, addedCalls);
            if (removedCallSearchEnabled) addRemovedCallQueries(queries, removedCalls);
            queries.addAll(buildFilterQueries(info, changedSymbols, removedSymbols));
            if (interfaceRelationSearchEnabled) addCalledImplementationQueries(queries, info, changedSymbols, addedCalls);
            queries = dedupByFilter(queries);
            if (queries.isEmpty()) return StructuralSearchResult.EMPTY;

            List<FilterQueryResult> queryResults = executeInParallel(queries, vector, namespace);

            // 취합은 순차로. 실행 순서는 병렬이라 비결정적이지만, 취합을 queries 순서대로
            // 하면 "먼저 등록된 관계의 태그가 유지된다"는 규칙이 항상 같게 적용된다.
            Set<String> seenIds = new HashSet<>();
            List<SearchHit> results = new ArrayList<>();
            Map<String, String> tags = new HashMap<>();

            for (FilterQueryResult qr : queryResults) {
                if (qr.relationshipTag().startsWith(CALLED_IMPL_TAG) && qr.hits().size() > MAX_CALLED_IMPLEMENTERS) continue;
                for (SearchHit r : qr.hits()) {
                    if (seenIds.add(r.id())) {
                        results.add(r);
                        tags.put(r.id(), qr.relationshipTag());
                    }
                }
            }

            // 새 코드가 부르는 메서드라도 getter·인터페이스 선언이면 관계 순위를 뒤로 내린다.
            // 슬롯에서만 빼면 높은 관계 순위 덕에 RRF로 다시 상위권에 올라와서, 리랭커가 판단하게 한다.
            if (addedCallDemoteTrivial) {
                for (SearchHit r : results) {
                    String tag = tags.get(r.id());
                    if (tag != null && tag.startsWith("사용호출(추가줄") && trivialMethodDetector.isTrivial(r.getString("code"))) {
                        tags.put(r.id(), ADDED_CALL_TRIVIAL_TAG + tag.substring(tag.indexOf(':') + 1));
                    }
                }
            }

            log.info("[RAG-구조] filePath={}, 변경심볼={}개, 사라진심볼={}개, 쿼리={}회, 후보={}개",
                filePath, changedSymbols.size(), removedSymbols.size(),
                queries.size(), results.size());

            return new StructuralSearchResult(results, tags);

        } catch (Exception e) {
            log.warn("[RAG-구조] 구조적 검색 실패, vector search만 사용. filePath={}", filePath, e);
            ragMetrics.fallback(RagMetrics.Stage.STRUCTURAL_SEARCH);
            return StructuralSearchResult.EMPTY;
        }
    }

    /**
     * 구조 검색 결과 중 우선순위가 높은 것들을 클래스당 1개로 선별한다.
     * (일반 후보와 별개로, RagContextService가 최종 결합 시 사용)
     *
     * 확정 슬롯은 "관계가 확실해 재순위가 불필요하다"는 근거로 rerank를 우회하므로,
     * 그 조건을 만족하지 않는 후보는 슬롯이 남더라도 채우지 않는다.
     */
    public List<SearchHit> selectTopStructural(
        StructuralSearchResult result, Set<String> changedFilesInPr, int limit) {

        // 강한 관계만 확정 슬롯 후보다. 정렬 전에 걸러 정렬 대상 자체를 줄인다.
        List<SearchHit> sorted = result.candidates().stream()
            .filter(r -> isStrongRelation(result.relationshipTags().getOrDefault(r.id(), "")))
            .filter(r -> !(addedCallSlotSkipTrivial
                && result.relationshipTags().getOrDefault(r.id(), "").startsWith(ADDED_CALL_TAG)
                && trivialMethodDetector.isTrivial(r.getString("code"))))
            .collect(Collectors.toCollection(ArrayList::new));

        if (sorted.isEmpty()) {
            log.info("[RAG-구조] 강한 관계 후보 없음, 확정 슬롯 비움");
            return List.of();
        }

        // priorityOf는 PRIORITY_ORDER를 순회하므로 비교마다 호출하면 비용이 쌓인다.
        Map<String, Integer> priorityCache = new HashMap<>();
        sorted.sort((a, b) -> {
            int pa = priorityCache.computeIfAbsent(a.id(),
                id -> priorityOf(result.relationshipTags().getOrDefault(id, "")));
            int pb = priorityCache.computeIfAbsent(b.id(),
                id -> priorityOf(result.relationshipTags().getOrDefault(id, "")));
            if (pa != pb) return Integer.compare(pa, pb);
            // 같은 우선순위면 벡터 유사도 높은 순.
            // 없으면 "어느 필터가 먼저 실행됐나"라는 무의미한 기준으로 결정된다.
            return Float.compare(b.score(), a.score());
        });

        Set<String> seenClasses = new HashSet<>();
        List<SearchHit> diversified = new ArrayList<>();
        for (SearchHit r : sorted) {
            if (changedFilesInPr.contains(r.getString("filePath"))) continue;
            String cls = r.getString("className");
            if (!seenClasses.add(cls)) continue;
            diversified.add(r);
            if (diversified.size() >= limit) break;
        }

        log.info("[RAG-구조] 선택된 structural {}개: {}", diversified.size(),
            diversified.stream()
                .map(r -> r.getString("className") + "("
                    + result.relationshipTags().getOrDefault(r.id(), "?") + ")")
                .toList());

        return diversified;
    }

    /**
     * 관계 태그의 우선순위(작을수록 리뷰에 중요). 태그가 없으면(벡터 검색 후보) 가장 낮다.
     * RagContextService가 rerank 순위와 합칠 때 쓴다.
     */
    public int relationPriority(String tag) {
        return tag == null ? PRIORITY_ORDER.size() + 1 : priorityOf(tag);
    }

    private int priorityOf(String tag) {
        for (int i = 0; i < PRIORITY_ORDER.size(); i++) {
            if (tag.startsWith(PRIORITY_ORDER.get(i))) return i;
        }
        return PRIORITY_ORDER.size();
    }

    /**
     * 필터 쿼리들을 병렬 실행한다. 벡터 저장소 호출은 I/O 바운드라 직렬이면
     * (쿼리 수 × 왕복시간)이 그대로 쌓이지만, 병렬이면 가장 느린 하나로 수렴한다.
     * 반환 순서는 입력 queries 순서와 동일하게 유지한다 — 결정성 확보.
     */
    private List<FilterQueryResult> executeInParallel(
        List<FilterQuery> queries, float[] vector, String namespace) {

        List<Future<FilterQueryResult>> futures = queries.stream()
            .map(q -> structuralSearchExecutor.submit(() -> new FilterQueryResult(
                q.relationshipTag(),
                qdrantClient.search(namespace, vector, limitOf(q), q.must()))))
            .toList();

        List<FilterQueryResult> results = new ArrayList<>(futures.size());
        for (Future<FilterQueryResult> f : futures) {
            try {
                results.add(f.get());
            } catch (ExecutionException e) {
                // 개별 필터 실패가 구조 검색 전체를 무너뜨리지 않게 한다.
                // 구조 검색은 보조 경로라 일부가 빠져도 벡터 검색 결과가 남는다.
                log.warn("[RAG-구조] 개별 필터 쿼리 실패, 건너뜀", e.getCause());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("구조 검색 중 인터럽트", e);
            }
        }
        return results;
    }

    /**
     * 호출자 계열(이름만·파일전체 포함)은 상한을 늘린다. 이름만·파일전체를 10으로 줄여 노이즈를 덜어 보았으나,
     * 11~30위에 있던 정답까지 잘려 평가에서 후보 Recall이 7.8%p 떨어졌다.
     */
    private int limitOf(FilterQuery query) {
        return query.relationshipTag().startsWith("호출자") ? CALLER_CANDIDATE_SIZE : METADATA_CANDIDATE_SIZE;
    }

    /**
     * 변경 심볼별로 "영향받는 코드를 찾는" 필터 쿼리를 조립한다.
     * 출발점이 변경 심볼이므로, 안 바뀐 메서드의 관계는 애초에 검색하지 않는다.
     *
     * @param changedSymbols 비어 있으면(diff 매핑 실패 등) 파일 전체 기준으로 폴백한다.
     * @param removedSymbols 사라진 메서드 이름. 변경 심볼 유무와 무관하게 항상 검색한다.
     */
    private List<FilterQuery> buildFilterQueries(
        StructuralInfo info, List<ChangedSymbol> changedSymbols, Set<String> removedSymbols) {

        List<FilterQuery> queries = new ArrayList<>();

        // 사라진 심볼은 변경 후 파일에 없어서 changedSymbols로는 잡히지 않는다.
        // 폴백 여부와 상관없이 먼저 넣어, 우선순위 정렬에서 최상위를 차지하게 한다.
        addRemovedSymbolQueries(queries, info, removedSymbols);

        if (changedSymbols.isEmpty()) {
            queries.addAll(buildFileWideQueries(info));
            queries.removeIf(Objects::isNull);
            return dedupByFilter(queries);
        }

        String className = info.className();
        if (className == null) {
            queries.removeIf(Objects::isNull);
            return dedupByFilter(queries);
        }

        Set<String> changedHeaders = new LinkedHashSet<>();

        for (ChangedSymbol symbol : changedSymbols) {
            // 심볼이 실제로 선언된 클래스 기준으로 찾는다. 같은 파일이라도 내부 Builder의 build()와
            // 바깥 클래스의 메서드는 호출자를 찾을 대상 클래스가 다르다.
            String owner = symbol.ownerClass() == null || symbol.ownerClass().isEmpty()
                ? className : symbol.ownerClass();
            switch (symbol.kind()) {
                case METHOD_SIGNATURE -> {
                    queries.add(eqQuery("calledMethodQualified", owner + "." + symbol.name(),
                        "호출자: " + owner + "." + symbol.name() + "()를 호출하는 코드"));
                    queries.add(guessedCallerQuery(owner, symbol.name(), "호출자(추정): "));
                    addInterfaceCallerQueries(queries, info, owner, symbol.name());
                    addImplementorQueries(queries, owner);
                    addUsedCallQueries(queries, info, symbol.name());
                }
                case METHOD_BODY -> {
                    // 같은 청크가 여러 쿼리에 걸리면 먼저 등록된 쿼리의 태그가 남으므로,
                    // 정밀한 것(확정 → 추정 → 이름만) 순서로 넣는다.
                    queries.add(eqQuery("calledMethodQualified", owner + "." + symbol.name(),
                        "호출자: " + owner + "." + symbol.name() + "()를 호출하는 코드"));
                    queries.add(guessedCallerQuery(owner, symbol.name(), "호출자(추정): "));
                    addInterfaceCallerQueries(queries, info, owner, symbol.name());
                    queries.add(eqQuery("calledMethodNames", symbol.name(),
                        "호출자(이름만): " + symbol.name() + "()를 호출하는 코드"));
                    addUsedCallQueries(queries, info, symbol.name());
                }
                case FIELD -> {
                    queries.add(eqQuery("referencedTypeNames", owner,
                        "참조자: " + owner + "를 참조하는 코드"));
                }
                case CLASS_HEADER -> changedHeaders.add(owner);
            }
        }

        // 클래스 헤더(extends/implements) 변경 → 그 클래스의 구현체·자식 전부.
        changedHeaders.forEach(header -> addImplementorQueries(queries, header));

        queries.removeIf(Objects::isNull);
        // 같은 필터가 여러 심볼에서 중복 생성될 수 있으므로 dedup.
        return dedupByFilter(queries);
    }

    /**
     * 리네이밍/삭제로 사라진 메서드의 호출부를 찾는 쿼리.
     *
     * changedSymbols는 변경 "후" 파일 기준이라 새 이름만 담고 있고, 옛 이름으로
     * 호출하던 기존 코드는 그 쿼리로 절대 매칭되지 않는다(0건). 이 호출부들은
     * 컴파일이 확정적으로 깨지는 지점이라 리뷰에서 가장 중요하므로 별도로 검색한다.
     */
    private void addRemovedSymbolQueries(
        List<FilterQuery> queries, StructuralInfo info, Set<String> removedSymbols) {

        String className = info.className();
        if (className == null || removedSymbols.isEmpty()) return;

        removedSymbols.stream()
            .filter(MethodNameFilter::isMeaningful)
            .limit(MAX_REMOVED_SYMBOL_QUERIES)
            .forEach(name -> {
                queries.add(eqQuery(
                    "calledMethodQualified", className + "." + name,
                    "호출자(삭제됨): " + className + "." + name + "()를 호출하는 코드"));
                queries.add(guessedCallerQuery(className, name, "호출자(삭제됨,추정): "));
            });
    }

    /**
     * 대상 클래스를 변수 이름으로 추정한 호출부를 찾는 쿼리.
     *
     * calledMethodQualified에는 확정된 호출만 들어 있어 이런 호출부는 이름만 매칭(동명이인 포함)으로만
     * 찾을 수 있었다. calledMethodRefs 객체 배열에 nested 조건을 걸어, "같은 호출 안에서"
     * 메서드 이름·추정 클래스·추정 여부가 모두 일치하는 청크만 가져온다.
     * resolved=false로 한정해 확정 호출자 쿼리와 결과가 겹치지 않게 한다.
     */
    private FilterQuery guessedCallerQuery(String className, String methodName, String tagPrefix) {
        if (!guessedCallerSearchEnabled) return null;
        if (className == null || methodName == null || methodName.isEmpty()) return null;
        return new FilterQuery(List.of(new Nested("calledMethodRefs", List.of(
                new Match("methodName", methodName),
                new Match("targetClassHint", className),
                new Match("resolved", false)))),
            tagPrefix + className + "." + methodName + "()를 호출하는 것으로 추정되는 코드");
    }

    /**
     * 바뀐 메서드를 인터페이스(또는 부모 클래스) 타입으로 부르는 호출자.
     * 예: JwtGenerator.generate()가 바뀌면 OAuth2TokenGenerator 타입 필드로 generate()를 부르는 Provider들.
     * 인터페이스 정보는 파일의 대표 클래스 것이라, 심볼이 그 클래스에 선언됐을 때만 쓴다.
     */
    private void addInterfaceCallerQueries(List<FilterQuery> queries, StructuralInfo info,
                                           String owner, String methodName) {
        if (!interfaceRelationSearchEnabled || !interfaceCallerSearchEnabled || !owner.equals(info.className())) return;
        if (!MethodNameFilter.isMeaningful(methodName)) return;
        List<String> parents = new ArrayList<>(info.interfaceNames());
        if (info.superClassName() != null) parents.add(info.superClassName());
        parents.stream()
            .filter(type -> ProjectTypeFilter.isProjectType(type, info.imports(), info.packageName()))
            .distinct()
            .forEach(type -> {
                String target = type + "." + methodName;
                queries.add(eqQuery("calledMethodQualified", target,
                    INTERFACE_CALLER_TAG + " " + target + "()로 " + owner + "를 부를 수 있는 코드"));
                queries.add(guessedCallerQuery(type, methodName, INTERFACE_CALLER_TAG + " "));
            });
    }

    /**
     * 새 코드·바뀐 메서드가 부르는 인터페이스 메서드의 구현체.
     * 예: authorizationService.findByToken() → InMemory·Jdbc OAuth2AuthorizationService의 findByToken.
     * 대상이 인터페이스가 아니면 결과가 0건이라 그대로 지나간다. 추가된 줄의 호출을 먼저 본다.
     */
    private void addCalledImplementationQueries(List<FilterQuery> queries, StructuralInfo info,
                                                List<ChangedSymbol> changedSymbols, List<CalledMethodRef> addedCalls) {
        Set<String> changedMethods = changedSymbols.stream()
            .filter(s -> s.kind() == ChangedSymbol.Kind.METHOD_SIGNATURE || s.kind() == ChangedSymbol.Kind.METHOD_BODY)
            .map(ChangedSymbol::name)
            .collect(Collectors.toSet());
        List<CalledMethodRef> refs = new ArrayList<>(addedCalls == null ? List.of() : addedCalls);
        info.calledMethodRefs().stream()
            .filter(ref -> changedMethods.contains(ref.fromMethodName()))
            .forEach(refs::add);

        refs.stream()
            .filter(ref -> ref.targetClassHint() != null && MethodNameFilter.isMeaningful(ref.methodName()))
            .filter(ref -> ProjectTypeFilter.isProjectType(ref.targetClassHint(), info.imports(), info.packageName()))
            .map(ref -> ref.targetClassHint() + "#" + ref.methodName())
            .distinct()
            .limit(MAX_CALLED_IMPL_QUERIES)
            .forEach(target -> {
                String type = target.substring(0, target.indexOf('#'));
                String method = target.substring(target.indexOf('#') + 1);
                FilterQuery q = compoundQuery("interfaceNames", type, "methodName", method,
                    CALLED_IMPL_TAG + " 변경 코드가 부르는 " + type + "." + method + "()의 구현");
                if (q != null) queries.add(q);
            });
    }

    /** 이 클래스의 구현체·자식클래스를 찾는 쿼리 (시그니처/헤더 변경 시 공통). */
    private void addImplementorQueries(List<FilterQuery> queries, String className) {
        queries.add(eqQuery("interfaceNames", className,
            "구현체: " + className + "를 구현하는 클래스"));
        queries.add(eqQuery("superClassName", className,
            "자식클래스: " + className + "를 상속하는 클래스"));
    }

    private FilterQuery eqQuery(String field, String value, String tag) {
        if (value == null || value.isEmpty()) return null;
        return new FilterQuery(List.of(new Match(field, value)), tag);
    }

    /**
     * 폴백: 변경 심볼을 특정하지 못했을 때 파일 전체 구조를 기준으로 쿼리를 만든다.
     * 정밀하게 좁히진 못하지만, 구조 검색이 통째로 비는 것보다는 넓게라도 잡는 편이 낫다.
     */
    private List<FilterQuery> buildFileWideQueries(StructuralInfo info) {
        List<FilterQuery> queries = new ArrayList<>();

        if (info.superClassName() != null) {
            queries.add(eqQuery("superClassName", info.superClassName(),
                "형제(상속): 같은 부모 " + info.superClassName() + "를 상속"));
        }

        for (String iface : info.interfaceNames().stream()
            .limit(MAX_INTERFACE_QUERIES).toList()) {
            queries.add(eqQuery("interfaceNames", iface,
                "형제(구현): 같은 인터페이스 " + iface + "를 구현"));
        }

        if (info.className() != null) {
            queries.add(eqQuery("interfaceNames", info.className(),
                "구현체: " + info.className() + "를 구현하는 클래스"));
            queries.add(eqQuery("superClassName", info.className(),
                "자식클래스: " + info.className() + "를 상속하는 클래스"));
            queries.add(eqQuery("referencedTypeNames", info.className(),
                "참조자: " + info.className() + "를 참조하는 코드"));

            List<String> meaningfulMethods = info.methodNames().stream()
                .filter(MethodNameFilter::isMeaningful)
                .limit(MAX_METHOD_QUERIES).toList();

            // 폴백 경로의 호출자 쿼리는 "변경된 메서드"가 아니라 파일의 임의 메서드
            // 기준이다. 관계 자체는 정확하지만 PR과 무관할 수 있으므로, 정밀 경로의
            // "호출자:"와 태그를 분리해 확정 슬롯 대상에서 제외한다.
            for (String methodName : meaningfulMethods) {
                queries.add(eqQuery("calledMethodQualified", info.className() + "." + methodName,
                    "호출자(파일전체): " + info.className() + "." + methodName + "()를 호출하는 코드"));
                queries.add(guessedCallerQuery(info.className(), methodName, "호출자(파일전체,추정): "));
            }

            // 폴백: 대상 클래스가 추정이거나 인덱싱 시점에 확정되지 않아
            // calledMethodQualified가 비어 있을 수 있으므로 이름만 매칭도 남긴다.
            for (String methodName : meaningfulMethods) {
                queries.add(eqQuery("calledMethodNames", methodName,
                    "호출자(이름만): 변경된 클래스의 메서드를 호출하는 코드"));
            }
        }

        for (String type : info.referencedTypeNames().stream()
            .filter(t -> ProjectTypeFilter.isProjectType(t, info.imports(), info.packageName()))
            .limit(MAX_REFERENCED_TYPE_QUERIES).toList()) {
            queries.add(eqQuery("className", type, "사용타입: 변경 코드가 " + type + "을 사용"));
        }

        addUsedCallQueries(queries, info.calledMethodRefs());

        queries.removeIf(Objects::isNull);
        return queries;
    }

    /**
     * 필터 내용이 동일한 쿼리를 제거한다.
     * 변경 심볼이 여러 개면 같은 필터(예: 구현체 검색)가 중복 생성될 수 있는데,
     * 같은 쿼리를 여러 번 날리는 건 낭비다.
     * 먼저 등장한 쿼리를 유지한다 — buildFilterQueries가 우선순위 순으로 넣으므로
     * 더 중요한 관계의 태그가 남는다.
     */
    private List<FilterQuery> dedupByFilter(List<FilterQuery> queries) {
        Set<String> seenFilters = new HashSet<>();
        List<FilterQuery> deduped = new ArrayList<>();
        for (FilterQuery q : queries) {
            // Match는 레코드라 toString()이 내용 기반으로 안정적이다.
            // 조건이 같으면 같은 문자열이 나오므로 dedup 키로 쓸 수 있다.
            String key = q.must().toString();
            if (seenFilters.add(key)) {
                deduped.add(q);
            }
        }
        return deduped;
    }

    private FilterQuery compoundQuery(String field1, String value1,
        String field2, String value2, String tag) {
        if (value1 == null || value1.isEmpty() || value2 == null || value2.isEmpty()) return null;
        return new FilterQuery(
            List.of(new Match(field1, value1), new Match(field2, value2)), tag);
    }

    /**
     * 추가된 줄의 호출을 클래스+메서드로 정확히 조회한다.
     * 넘치면 대상 클래스가 확정된 호출부터 남기고, 같은 등급은 코드에 나온 순서를 따른다.
     */
    private void addAddedCallQueries(List<FilterQuery> queries, List<CalledMethodRef> addedCalls) {
        addCallQueries(queries, addedCalls, MAX_ADDED_CALL_QUERIES,
            ADDED_CALL_TAG, "사용호출(추가줄,추정):", " 새 코드가 ");
    }

    /** 삭제된 줄의 호출. 지운 코드가 하던 일을 어디서 대신하는지 리뷰가 확인할 수 있게 원래 대상을 가져온다. */
    private void addRemovedCallQueries(List<FilterQuery> queries, List<CalledMethodRef> removedCalls) {
        addCallQueries(queries, removedCalls, MAX_REMOVED_CALL_QUERIES,
            REMOVED_CALL_TAG, "사용호출(삭제줄,추정):", " 삭제된 코드가 ");
    }

    private void addCallQueries(List<FilterQuery> queries, List<CalledMethodRef> calls, int limit,
                                String resolvedTag, String guessedTag, String subject) {
        if (calls == null) return;
        List<CalledMethodRef> ordered = calls.stream()
            .sorted(Comparator.comparing(ref -> !ref.resolved()))
            .limit(limit)
            .toList();
        for (CalledMethodRef ref : ordered) {
            String target = ref.targetClassHint() + "." + ref.methodName();
            FilterQuery q = compoundQuery("className", ref.targetClassHint(), "methodName", ref.methodName(),
                (ref.resolved() ? resolvedTag : guessedTag) + subject + target + "()를 호출");
            if (q != null) queries.add(q);
        }
    }

    /**
     * 변경된 메서드가 호출하는 대상을 컨텍스트로 가져온다.
     * 기존 buildFileWideQueries의 "사용호출:" 로직과 동일한 개념을,
     * 변경 심볼이 확정된 정밀 경로에서도 쓸 수 있도록 뽑아낸 것이다.
     */
    private void addUsedCallQueries(List<FilterQuery> queries, StructuralInfo info, String fromMethodName) {
        addUsedCallQueries(queries, info.calledMethodRefs().stream()
            .filter(ref -> fromMethodName.equals(ref.fromMethodName()))
            .toList());
    }

    /**
     * 호출 대상 메서드를 찾는 쿼리.
     *
     * 대상 클래스 힌트가 있으면 className + methodName으로 정확히 찾는다. 이때는 get으로 시작해도
     * 거르지 않는다. OAuth2EndpointUtils.getFormParameters(request) 같은 정적 유틸은 이름만 getter일 뿐
     * 실제 로직이고, 진짜 getter는 3줄 미만이라 애초에 인덱싱되지 않으므로 걸러도 얻는 게 없다.
     * 힌트가 추정값이면 틀렸을 때(svc → Svc) 0건이 되므로, 의미 있는 이름에 한해 이름만 매칭도 남긴다.
     * 같은 청크는 먼저 들어간 정밀 쿼리의 태그가 남는다.
     */
    private void addUsedCallQueries(List<FilterQuery> queries, List<CalledMethodRef> refs) {
        refs.stream()
            .filter(ref -> !MethodNameFilter.isCommonObjectMethod(ref.methodName()))
            .filter(ref -> ref.targetClassHint() != null || MethodNameFilter.isMeaningful(ref.methodName()))
            .limit(MAX_CALLED_REF_QUERIES)
            .forEach(ref -> {
                String target = ref.targetClassHint() + "." + ref.methodName();
                if (ref.targetClassHint() != null) {
                    queries.add(compoundQuery(
                        "className", ref.targetClassHint(), "methodName", ref.methodName(),
                        (ref.resolved() ? "사용호출: " : "사용호출(추정): ")
                            + "변경 코드가 " + target + "()를 호출"));
                }
                if (!ref.resolved() && MethodNameFilter.isMeaningful(ref.methodName())) {
                    queries.add(eqQuery("methodName", ref.methodName(),
                        "사용호출(이름만): 변경 코드가 " + ref.methodName() + "()를 호출"));
                }
            });
    }
}