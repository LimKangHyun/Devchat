package project.ai.rag;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import io.pinecone.unsigned_indices_model.ScoredVectorWithUnsignedIndices;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import project.ai.client.PineconeClient;
import project.ai.indexing.chunking.ChunkMeta.CalledMethodRef;
import project.ai.indexing.structural.ChangedSymbol;
import project.ai.indexing.structural.MethodNameFilter;
import project.ai.indexing.structural.ProjectTypeFilter;
import project.ai.indexing.structural.StructuralInfo;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import static project.ai.rag.PineconeResultUtils.getStringField;

/**
 * 구조적 관계(구현/상속/호출/참조) 기반 검색을 담당한다.
 *
 * Pinecone 메타데이터 필터를 OR로 묶으면 "어느 조건으로 매칭됐는지"를 알 수 없다.
 * 그 정보가 곧 관계 태그이고 우선순위 정렬의 기준이므로, 관계별로 쿼리를 나누는 것은
 * 설계상 불가피하다. 대신 두 가지로 비용을 통제한다.
 *  1) 결과가 나올 수 없는 쿼리(JDK/외부 타입)는 만들기 전에 거른다.
 *  2) 남은 쿼리는 서로 독립적이므로 병렬 실행한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StructuralSearchService {

    private final PineconeClient pineconeClient;

    @Qualifier("structuralSearchExecutor")
    private final ExecutorService structuralSearchExecutor;

    private static final int METADATA_CANDIDATE_SIZE = 10;
    private static final int MAX_INTERFACE_QUERIES = 3;
    private static final int MAX_REFERENCED_TYPE_QUERIES = 5;
    private static final int MAX_METHOD_QUERIES = 3;
    private static final int MAX_CALLED_REF_QUERIES = 5;
    private static final int MAX_REMOVED_SYMBOL_QUERIES = 5;

    // 변경으로 "깨질 수 있는" 쪽(변경된 파일을 쓰는 코드)을 먼저 가져온다.
    // 인터페이스/부모 시그니처가 바뀌면 구현체·상속체가 컴파일 에러가 나므로 리뷰에 필수.
    // 반대로 "변경 코드가 쓰는 타입/메서드"는 이 PR에서 안 바뀌므로 깨지지 않는다(맥락용).
    // "호출자(삭제됨):"이 최상위인 이유: 사라진 메서드를 호출하는 코드는 추정이 아니라
    // 컴파일 에러가 확정이다.
    // 확정/추정 구분: 대상 클래스를 선언 타입에서 읽은 것(사용호출)이 이름 관례로 추정한
    // 것(사용호출(추정))보다 신뢰도가 높다.
    // 주의: 이 문자열은 buildFilterQueries()/buildFileWideQueries()가 붙이는 태그와
    //       startsWith로 매칭되므로, 태그 문구를 수정하면 여기와
    //       STRONG_RELATION_TAGS도 함께 고쳐야 한다.
    private static final List<String> PRIORITY_ORDER = List.of(
        "호출자(삭제됨):", "구현체:", "자식클래스:", "호출자:", "참조자:",
        "호출자(이름만):", "호출자(파일전체):",
        "형제(구현):", "형제(상속):", "사용타입:", "사용호출:", "사용호출(추정):"
    );

    /**
     * 확정 슬롯(rerank 우회)에 넣을 수 있는 관계.
     * 구현체/자식클래스는 AST 구조상 확정이고, "호출자:"는 calledMethodQualified
     * (클래스명까지 확정된 호출)로 매칭된 결과라 대상이 특정된다.
     * "호출자(삭제됨):"은 리네이밍/삭제된 메서드의 호출부라 컴파일이 확실히 깨진다.
     * "호출자(파일전체):"는 변경 심볼을 못 구했을 때 파일의 임의 메서드 기준으로 만든
     * 쿼리라, 관계는 정확해도 PR과 무관할 수 있어 제외한다.
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
        return false;
    }

    public record StructuralSearchResult(
        List<ScoredVectorWithUnsignedIndices> candidates,
        Map<String, String> relationshipTags
    ) {
        public static final StructuralSearchResult EMPTY =
            new StructuralSearchResult(List.of(), Map.of());
    }

    private record FilterQuery(Struct filter, String relationshipTag) {}

    private record FilterQueryResult(
        String relationshipTag,
        List<ScoredVectorWithUnsignedIndices> hits
    ) {}

    /**
     * @param removedSymbols 변경 전에는 있었으나 변경 후 사라진 메서드 이름
     *                       (리네이밍된 옛 이름 또는 삭제된 메서드)
     */
    public StructuralSearchResult search(
        float[] vector, String namespace, String filePath, StructuralInfo info,
        List<ChangedSymbol> changedSymbols, Set<String> removedSymbols) {

        try {
            if (info.isEmpty()) return StructuralSearchResult.EMPTY;

            List<FilterQuery> queries = buildFilterQueries(info, changedSymbols, removedSymbols);
            if (queries.isEmpty()) return StructuralSearchResult.EMPTY;

            List<FilterQueryResult> queryResults = executeInParallel(queries, vector, namespace);

            // 취합은 순차로. 실행 순서는 병렬이라 비결정적이지만, 취합을 queries 순서대로
            // 하면 "먼저 등록된 관계의 태그가 유지된다"는 규칙이 항상 같게 적용된다.
            Set<String> seenIds = new HashSet<>();
            List<ScoredVectorWithUnsignedIndices> results = new ArrayList<>();
            Map<String, String> tags = new HashMap<>();

            for (FilterQueryResult qr : queryResults) {
                for (ScoredVectorWithUnsignedIndices r : qr.hits()) {
                    if (seenIds.add(r.getId())) {
                        results.add(r);
                        tags.put(r.getId(), qr.relationshipTag());
                    }
                }
            }

            log.info("[RAG-구조] filePath={}, 변경심볼={}개, 사라진심볼={}개, 쿼리={}회, 후보={}개",
                filePath, changedSymbols.size(), removedSymbols.size(),
                queries.size(), results.size());

            return new StructuralSearchResult(results, tags);

        } catch (Exception e) {
            log.warn("[RAG-구조] 구조적 검색 실패, vector search만 사용. filePath={}", filePath, e);
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
    public List<ScoredVectorWithUnsignedIndices> selectTopStructural(
        StructuralSearchResult result, Set<String> changedFilesInPr, int limit) {

        // 강한 관계만 확정 슬롯 후보다. 정렬 전에 걸러 정렬 대상 자체를 줄인다.
        List<ScoredVectorWithUnsignedIndices> sorted = result.candidates().stream()
            .filter(r -> isStrongRelation(result.relationshipTags().getOrDefault(r.getId(), "")))
            .collect(Collectors.toCollection(ArrayList::new));

        if (sorted.isEmpty()) {
            log.info("[RAG-구조] 강한 관계 후보 없음, 확정 슬롯 비움");
            return List.of();
        }

        // priorityOf는 PRIORITY_ORDER를 순회하므로 비교마다 호출하면 비용이 쌓인다.
        Map<String, Integer> priorityCache = new HashMap<>();
        sorted.sort((a, b) -> {
            int pa = priorityCache.computeIfAbsent(a.getId(),
                id -> priorityOf(result.relationshipTags().getOrDefault(id, "")));
            int pb = priorityCache.computeIfAbsent(b.getId(),
                id -> priorityOf(result.relationshipTags().getOrDefault(id, "")));
            if (pa != pb) return Integer.compare(pa, pb);
            // 같은 우선순위면 벡터 유사도 높은 순.
            // 없으면 "어느 필터가 먼저 실행됐나"라는 무의미한 기준으로 결정된다.
            return Float.compare(b.getScore(), a.getScore());
        });

        Set<String> seenClasses = new HashSet<>();
        List<ScoredVectorWithUnsignedIndices> diversified = new ArrayList<>();
        for (ScoredVectorWithUnsignedIndices r : sorted) {
            if (changedFilesInPr.contains(getStringField(r, "filePath"))) continue;
            String cls = getStringField(r, "className");
            if (!seenClasses.add(cls)) continue;
            diversified.add(r);
            if (diversified.size() >= limit) break;
        }

        log.info("[RAG-구조] 선택된 structural {}개: {}", diversified.size(),
            diversified.stream()
                .map(r -> getStringField(r, "className") + "("
                    + result.relationshipTags().getOrDefault(r.getId(), "?") + ")")
                .toList());

        return diversified;
    }

    private int priorityOf(String tag) {
        for (int i = 0; i < PRIORITY_ORDER.size(); i++) {
            if (tag.startsWith(PRIORITY_ORDER.get(i))) return i;
        }
        return PRIORITY_ORDER.size();
    }

    /**
     * 필터 쿼리들을 병렬 실행한다. Pinecone 호출은 I/O 바운드라 직렬이면
     * (쿼리 수 × 왕복시간)이 그대로 쌓이지만, 병렬이면 가장 느린 하나로 수렴한다.
     * 반환 순서는 입력 queries 순서와 동일하게 유지한다 — 결정성 확보.
     */
    private List<FilterQueryResult> executeInParallel(
        List<FilterQuery> queries, float[] vector, String namespace) {

        List<Future<FilterQueryResult>> futures = queries.stream()
            .map(q -> structuralSearchExecutor.submit(() -> new FilterQueryResult(
                q.relationshipTag(),
                pineconeClient.queryWithFilter(
                    vector, METADATA_CANDIDATE_SIZE, namespace, q.filter()))))
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

        boolean classHeaderChanged = false;

        for (ChangedSymbol symbol : changedSymbols) {
            switch (symbol.kind()) {
                case METHOD_SIGNATURE -> {
                    // 시그니처 변경 → 호출자 + 구현체 + 자식 전부가 깨질 수 있다.
                    queries.add(eqQuery("calledMethodQualified", className + "." + symbol.name(),
                        "호출자: " + className + "." + symbol.name() + "()를 호출하는 코드"));
                    // 이 메서드가 인터페이스/부모 메서드면 구현체·자식도 영향.
                    // 어느 인터페이스의 메서드인지는 구문만으론 확정 못 하므로,
                    // 이 클래스가 구현/상속한 것 전부를 후보로 넣는다.
                    addImplementorQueries(queries, info);
                }
                case METHOD_BODY -> {
                    // 본문 변경 → 동작이 바뀌므로 호출자만.
                    queries.add(eqQuery("calledMethodQualified", className + "." + symbol.name(),
                        "호출자: " + className + "." + symbol.name() + "()를 호출하는 코드"));
                    queries.add(eqQuery("calledMethodNames", symbol.name(),
                        "호출자(이름만): " + symbol.name() + "()를 호출하는 코드"));
                }
                case FIELD -> {
                    // 필드 변경 → 이 클래스를 참조/사용하는 코드.
                    queries.add(eqQuery("referencedTypeNames", className,
                        "참조자: " + className + "를 참조하는 코드"));
                }
                case CLASS_HEADER -> classHeaderChanged = true;
            }
        }

        // 클래스 헤더(extends/implements) 변경 → 구현체·자식 전부.
        if (classHeaderChanged) {
            addImplementorQueries(queries, info);
        }

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
            .forEach(name -> queries.add(eqQuery(
                "calledMethodQualified", className + "." + name,
                "호출자(삭제됨): " + className + "." + name + "()를 호출하는 코드")));
    }

    /** 이 클래스의 구현체·자식클래스를 찾는 쿼리 (시그니처/헤더 변경 시 공통). */
    private void addImplementorQueries(List<FilterQuery> queries, StructuralInfo info) {
        String className = info.className();
        queries.add(eqQuery("interfaceNames", className,
            "구현체: " + className + "를 구현하는 클래스"));
        queries.add(eqQuery("superClassName", className,
            "자식클래스: " + className + "를 상속하는 클래스"));
    }

    private FilterQuery eqQuery(String field, String value, String tag) {
        if (value == null || value.isEmpty()) return null;
        Struct filter = Struct.newBuilder().putFields(field, eqValue(value)).build();
        return new FilterQuery(filter, tag);
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
            }

            // 폴백: 대상 클래스가 추정이거나 인덱싱 시점에 확정되지 않아
            // calledMethodQualified가 비어 있을 수 있으므로 이름만 매칭도 남긴다.
            for (String methodName : meaningfulMethods) {
                queries.add(eqQuery("calledMethodNames", methodName,
                    "호출자(이름만): 변경된 클래스의 메서드를 호출하는 코드"));
            }
        }

        for (String type : info.referencedTypeNames().stream()
            .filter(t -> ProjectTypeFilter.isProjectType(t, info.imports()))
            .limit(MAX_REFERENCED_TYPE_QUERIES).toList()) {
            queries.add(eqQuery("className", type, "사용타입: 변경 코드가 " + type + "을 사용"));
        }

        for (CalledMethodRef ref : info.calledMethodRefs().stream()
            .filter(r -> MethodNameFilter.isMeaningful(r.methodName()))
            .limit(MAX_CALLED_REF_QUERIES).toList()) {
            if (ref.resolved()) {
                // 선언 타입에서 확정된 힌트 - className + methodName AND로 동명이인 배제
                queries.add(compoundQuery(
                    "className", ref.targetClassHint(), "methodName", ref.methodName(),
                    "사용호출: 변경 코드가 " + ref.targetClassHint() + "." + ref.methodName() + "()를 호출"));
            } else {
                // 이름 관례 추정이거나 대상 불명 - 틀린 클래스로 걸면 0건이 되므로 메서드명만 매칭
                queries.add(eqQuery("methodName", ref.methodName(),
                    "사용호출(추정): 변경 코드가 " + ref.methodName() + "()를 호출"));
            }
        }

        queries.removeIf(Objects::isNull);
        return queries;
    }

    /**
     * 필터 내용이 동일한 쿼리를 제거한다.
     * 변경 심볼이 여러 개면 같은 필터(예: 구현체 검색)가 중복 생성될 수 있는데,
     * 같은 Pinecone 쿼리를 여러 번 날리는 건 낭비다.
     * 먼저 등장한 쿼리를 유지한다 — buildFilterQueries가 우선순위 순으로 넣으므로
     * 더 중요한 관계의 태그가 남는다.
     */
    private List<FilterQuery> dedupByFilter(List<FilterQuery> queries) {
        Set<String> seenFilters = new HashSet<>();
        List<FilterQuery> deduped = new ArrayList<>();
        for (FilterQuery q : queries) {
            // Struct는 protobuf라 toString()이 내용 기반으로 안정적이다.
            // 필터 구조가 같으면 같은 문자열이 나오므로 dedup 키로 쓸 수 있다.
            String key = q.filter().toString();
            if (seenFilters.add(key)) {
                deduped.add(q);
            }
        }
        return deduped;
    }

    private FilterQuery compoundQuery(String field1, String value1,
        String field2, String value2, String tag) {
        if (value1 == null || value1.isEmpty() || value2 == null || value2.isEmpty()) return null;
        Struct filter = Struct.newBuilder()
            .putFields(field1, eqValue(value1))
            .putFields(field2, eqValue(value2))
            .build();
        return new FilterQuery(filter, tag);
    }

    private Value eqValue(String value) {
        return Value.newBuilder()
            .setStructValue(Struct.newBuilder()
                .putFields("$eq", Value.newBuilder().setStringValue(value).build())
                .build())
            .build();
    }
}