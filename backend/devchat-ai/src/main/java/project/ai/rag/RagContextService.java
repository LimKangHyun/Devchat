package project.ai.rag;

import io.micrometer.core.instrument.Timer;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import project.ai.client.QdrantClient;
import project.ai.indexing.SparseEncoder;
import project.ai.indexing.structural.AddedCallResolver;
import project.ai.indexing.structural.ChangedSymbol;
import project.ai.indexing.structural.ChangedSymbolResolver;
import project.ai.indexing.chunking.ChunkMeta.CalledMethodRef;
import project.ai.indexing.chunking.AstChunkExtractor;
import project.ai.indexing.chunking.ChunkEmbeddingText;
import project.ai.indexing.chunking.ChunkMeta;
import project.ai.indexing.structural.StructuralInfo;
import project.ai.indexing.structural.StructuralInfoExtractor;
import project.ai.indexing.EmbeddingService;
import project.ai.processor.DiffLineParser;
import project.common.exception.ex.IndexingException;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;


@Slf4j
@Service
@RequiredArgsConstructor
public class RagContextService {

    private final EmbeddingService embeddingService;
    private final QdrantClient qdrantClient;
    private final StructuralInfoExtractor structuralInfoExtractor;
    private final StructuralSearchService structuralSearchService;
    private final RerankQueryService rerankQueryService;
    private final DiffLineParser diffLineParser;
    private final ChangedSymbolResolver changedSymbolResolver;
    private final SparseEncoder sparseEncoder;
    private final AddedCallResolver addedCallResolver;
    private final JavaParser javaParser;
    private final AstChunkExtractor astChunkExtractor;
    private final RagMetrics ragMetrics;

    private static final int TOP_K = 5;
    private static final int MAX_TOTAL_RESULTS = 9;
    private static final int MAX_STRUCTURAL_SLOTS = 3;
    private static final int VECTOR_CANDIDATE_SIZE = 30;

    /** 하이브리드 검색에서 dense·sparse 각각 가져올 후보 수. 한쪽에만 걸린 후보도 RRF에 들어오도록 넉넉히 둔다. */
    private static final int HYBRID_PREFETCH_SIZE = 60;

    /**
     * 첫 검색을 dense + BM25 sparse의 RRF 하이브리드로 할지. 끄면 dense만 쓴다.
     * 검색 품질 평가에서 두 방식을 비교하려고 설정으로 뺐다.
     */
    @Value("${rag.vector.hybrid:true}")
    private boolean hybridSearchEnabled;

    /**
     * 최종 선택에서 rerank 순위와 관계 우선순위를 RRF로 합칠지.
     * 리랭커는 diff와 "비슷하게 생긴" 코드(같은 이름의 메서드를 정의한 형제 클래스 등)를 높게 보는 반면,
     * 구조 검색의 관계 태그는 "그 메서드를 호출하는 코드"를 정확히 구분한다. 두 신호를 합쳐
     * 영향 관계가 있는 코드가 형제 코드에 밀리지 않게 한다.
     * 평가(PR 15개)에서 rerank만 쓸 때보다 Recall@5와 Hit@5가 높아 기본으로 켠다.
     */
    @Value("${rag.rerank.relation-fusion:true}")
    private boolean relationFusionEnabled;

    /**
     * 관계 순위를 후보를 한 줄로 세운 순서 대신 태그 등급(relationPriority)으로 매길지.
     * 줄 세운 순서는 태그 붙은 후보 수만큼 태그 없는 후보를 뒤로 밀고, 같은 등급 안에서는
     * rerank 순서를 다시 반영한다. 등급으로 매기면 같은 태그는 같은 순위를 받고,
     * 태그 없는 후보는 모두 맨 뒤 등급으로 동순위가 되어 rerank 순위만으로 갈린다.
     * 평가(PR 15개)에서 줄 세운 순서보다 Recall@5가 높아(47.1% → 49.4%) 기본으로 켠다.
     */
    @Value("${rag.rerank.relation-rank-by-tier:true}")
    private boolean relationRankByTier;

    /** 벡터 검색을 diff 하나 대신 바뀐 메서드별 질의로도 할지. 설명은 vectorSearch() 참고. */
    @Value("${rag.vector.per-method-query:true}")
    private boolean perMethodQueryEnabled;

    /**
     * 메서드 질의를 인덱싱 문서와 같은 "맥락 머리말 + 코드" 형식으로 만들지.
     * 맥락 머리말로 인덱싱한 컬렉션(rag.indexing.contextual-header)과 함께 쓴다.
     */
    @Value("${rag.vector.method-query-header:true}")
    private boolean methodQueryHeaderEnabled;

    private static final int MAX_METHOD_QUERIES = 3;
    private static final int METHOD_QUERY_MAX_CHARS = 2000;

    /** RRF 상수. 관례값(60)을 쓴다. 작을수록 상위 순위에 가중치가 몰린다. */
    private static final int RRF_K = 60;

    /**
     * 최종 결과를 topK개로 고정하고 클래스 다양화로 채울지. 끄면 같은 클래스가 겹친 만큼
     * 결과 개수를 늘리는(최대 maxTotal) 이전 방식을 쓴다. 평가에서 두 방식을 비교하려고 설정으로 뺐다.
     */
    @Value("${rag.final.diversify:false}")
    private boolean diversifyFinal;

    /** 다양화 시 한 클래스가 최종 결과에서 앞자리를 차지할 수 있는 최대 개수. */
    private static final int MAX_PER_CLASS = 2;

    /**
     * @param baseContent 변경 전(base) 파일 원문. 신규 파일이면 빈 문자열.
     */
    public String buildContext(Long repoId, String filePath, String diff,
        String fileContent, String baseContent,
        Set<String> changedFilesInPr) {
        Timer.Sample sample = Timer.start();
        try {
            List<SearchHit> results = search(
                repoId, filePath, diff, fileContent, baseContent,
                changedFilesInPr, TOP_K, MAX_TOTAL_RESULTS);

            if (results.isEmpty()) {
                log.info("[RAG] repoId={}, filePath={}, 검색결과 없음", repoId, filePath);
                ragMetrics.result(RagMetrics.Result.EMPTY);
                return "";
            }

            for (SearchHit r : results) {
                log.info("[RAG] reviewFile={}, candidatePath={}, class={}, method={}",
                    filePath, r.getString("filePath"),
                    r.getString("className"), r.getString("methodSignature"));
            }

            ragMetrics.result(RagMetrics.Result.OK);
            return formatContext(results);

        } catch (IndexingException e) {
            log.debug("RAG 임베딩 실패, RAG 없이 리뷰 진행. repoId={}, filePath={}", repoId, filePath);
            ragMetrics.result(RagMetrics.Result.EMBEDDING_FAILED);
            return "";
        } catch (Exception e) {
            log.warn("RAG 컨텍스트 조회 중 예상치 못한 오류. repoId={}, filePath={}", repoId, filePath, e);
            ragMetrics.result(RagMetrics.Result.ERROR);
            return "";
        } finally {
            sample.stop(ragMetrics.searchTimer());
        }
    }

    public List<SearchHit> searchForEvaluation(
        Long repoId, String filePath, String diff, String fileContent,
        String baseContent, int topK, Set<String> changedFilesInPr) {

        return search(repoId, filePath, diff, fileContent, baseContent, changedFilesInPr,
            topK, Math.max(topK, MAX_TOTAL_RESULTS));
    }

    /**
     * 검색 단계별 결과. 검색 품질 평가에서 rerank 전 후보와 최종 결과를 따로 측정하려고 쓴다.
     *
     * @param candidates rerank 전 후보 전체 (벡터 + 구조 검색, PR 변경 파일 제외, 중복 제거)
     * @param finalResults 리뷰 프롬프트에 실제로 들어가는 결과
     * @param structuralSlots 확정 슬롯으로 rerank 없이 들어간 구조 검색 결과
     * @param rerankFallback 리랭킹이 실패해 벡터 순서로 대체됐는지
     * @param rankedResults 동적 확장으로 자르기 전 순위 목록 (확정 슬롯 + 리랭크 결과, 최대 maxTotal개). 평가에서 K별 지표를 재려고 쓴다.
     */
    public record SearchTrace(
        List<SearchHit> vectorCandidates,
        StructuralSearchService.StructuralSearchResult structural,
        List<SearchHit> candidates,
        List<SearchHit> structuralSlots,
        List<SearchHit> finalResults,
        boolean rerankFallback,
        List<SearchHit> rankedResults
    ) {}

    /** 검색 쿼리 임베딩. 평가에서 같은 쿼리를 여러 설정으로 돌릴 때 한 번만 임베딩하려고 분리했다. */
    public float[] embedQuery(String filePath, String diff) {
        return embeddingService.embedQuery(queryText(filePath, diff));
    }

    /** dense 임베딩과 sparse 인코딩이 같은 쿼리 텍스트를 쓰도록 한곳에서 만든다. */
    private String queryText(String filePath, String diff) {
        return "File: " + filePath + "\nDiff:\n" + truncate(diff, 2000);
    }

    /**
     * 벡터 검색. 기본은 diff 원문 하나로 검색한다.
     * 메서드 단위 질의를 켜면 바뀐 메서드마다 "변경 후 코드"로 따로 검색해 diff 질의 결과와 RRF로 합친다.
     *  - diff는 2000자에서 잘려 긴 diff의 뒤쪽 변경이 질의에 안 들어갔다.
     *  - +/- 기호가 섞인 diff보다 일반 코드가 인덱스의 코드 조각과 모양이 같아 임베딩이 잘 맞는다.
     * 후보 수는 그대로(VECTOR_CANDIDATE_SIZE) 둬서 리랭크 비용과 비교 조건을 바꾸지 않는다.
     */
    private List<SearchHit> vectorSearch(String namespace, String filePath, String diff, String fileContent,
                                         Set<Integer> changedLines, float[] diffVector) {
        List<SearchHit> diffHits = vectorSearchOne(namespace, diffVector, queryText(filePath, diff));
        if (!perMethodQueryEnabled) return diffHits;

        List<String> methodQueries = changedMethodQueries(filePath, fileContent, changedLines);
        if (methodQueries.isEmpty()) return diffHits;

        List<List<SearchHit>> ranked = new ArrayList<>();
        ranked.add(diffHits);
        for (String query : methodQueries) {
            try {
                ranked.add(vectorSearchOne(namespace, embeddingService.embedQuery(query), query));
            } catch (Exception e) {
                log.warn("[RAG] 메서드 단위 벡터 질의 실패, 건너뜀. filePath={}", filePath, e);
                ragMetrics.fallback(RagMetrics.Stage.METHOD_QUERY);
            }
        }
        return fuseByRank(ranked, VECTOR_CANDIDATE_SIZE);
    }

    private List<SearchHit> vectorSearchOne(String namespace, float[] vector, String queryText) {
        return hybridSearchEnabled
            ? qdrantClient.hybridSearch(namespace, vector, sparseEncoder.encodeQuery(queryText),
                VECTOR_CANDIDATE_SIZE, HYBRID_PREFETCH_SIZE)
            : qdrantClient.search(namespace, vector, VECTOR_CANDIDATE_SIZE);
    }

    /**
     * 바뀐 메서드별 벡터 질의 텍스트. 변경 줄이 많은 순, 최대 MAX_METHOD_QUERIES개.
     * 질의 머리말을 켜면 인덱싱과 똑같은 청킹·머리말(ChunkEmbeddingText)을 써서 문서와 같은 형식으로 만든다.
     * 청크를 못 찾으면(시그니처 불일치 등) 그 메서드는 머리말 없는 형식으로 둔다.
     */
    private List<String> changedMethodQueries(String filePath, String fileContent, Set<Integer> changedLines) {
        if (fileContent == null || fileContent.isBlank() || changedLines.isEmpty()) return List.of();
        try {
            ParseResult<CompilationUnit> parsed = javaParser.parse(fileContent);
            if (parsed.getResult().isEmpty()) return List.of();
            record Touched(MethodDeclaration method, long changed) {}
            List<MethodDeclaration> touched = parsed.getResult().get().findAll(MethodDeclaration.class).stream()
                .map(m -> new Touched(m, m.getRange()
                    .map(r -> changedLines.stream().filter(l -> l >= r.begin.line && l <= r.end.line).count())
                    .orElse(0L)))
                .filter(t -> t.changed() > 0)
                .sorted(Comparator.comparingLong(Touched::changed).reversed())
                .limit(MAX_METHOD_QUERIES)
                .map(Touched::method)
                .toList();
            if (touched.isEmpty()) return List.of();

            Map<String, ChunkMeta> chunkBySignature = new HashMap<>();
            if (methodQueryHeaderEnabled) {
                for (ChunkMeta c : astChunkExtractor.extract(fileContent, filePath)) {
                    if (c.methodSignature() != null) chunkBySignature.putIfAbsent(c.methodSignature(), c);
                }
            }
            return touched.stream().map(m -> {
                ChunkMeta chunk = chunkBySignature.get(signatureOf(m));
                return chunk != null
                    ? truncate(ChunkEmbeddingText.withHeader(chunk), METHOD_QUERY_MAX_CHARS + 500)
                    : "File: " + filePath + "\nMethod:\n" + truncate(m.toString(), METHOD_QUERY_MAX_CHARS);
            }).toList();
        } catch (Exception e) {
            log.warn("[RAG] 바뀐 메서드 추출 실패, diff 질의만 사용. filePath={}", filePath, e);
            ragMetrics.fallback(RagMetrics.Stage.METHOD_QUERY);
            return List.of();
        }
    }

    /** 인덱싱(AstChunkExtractor)과 같은 규칙의 시그니처: 이름(파라미터 타입, ...). */
    private static String signatureOf(MethodDeclaration m) {
        return m.getNameAsString() + "(" + m.getParameters().stream()
            .map(p -> p.getType().asString()).collect(Collectors.joining(", ")) + ")";
    }

    /** 여러 검색 결과를 순위 기반 RRF로 합친다. 같은 조각은 처음 나온 SearchHit을 쓴다. */
    private static List<SearchHit> fuseByRank(List<List<SearchHit>> rankedLists, int limit) {
        Map<String, SearchHit> hitById = new LinkedHashMap<>();
        Map<String, Double> score = new HashMap<>();
        for (List<SearchHit> list : rankedLists) {
            for (int i = 0; i < list.size(); i++) {
                SearchHit h = list.get(i);
                hitById.putIfAbsent(h.id(), h);
                score.merge(h.id(), 1.0 / (RRF_K + i + 1), Double::sum);
            }
        }
        return hitById.values().stream()
            .sorted(Comparator.comparingDouble((SearchHit h) -> score.get(h.id())).reversed())
            .limit(limit)
            .toList();
    }

    public SearchTrace traceForEvaluation(
        Long repoId, String filePath, String diff, String fileContent,
        String baseContent, int topK, Set<String> changedFilesInPr, float[] queryVector) {

        return trace(repoId, filePath, diff, fileContent, baseContent, changedFilesInPr,
            topK, Math.max(topK, MAX_TOTAL_RESULTS), queryVector);
    }

    private List<SearchHit> search(
        Long repoId, String filePath, String diff, String fileContent,
        String baseContent, Set<String> changedFilesInPr, int topK, int maxTotal) {
        return trace(repoId, filePath, diff, fileContent, baseContent, changedFilesInPr, topK, maxTotal,
            embedQuery(filePath, diff))
            .finalResults();
    }

    private SearchTrace trace(
        Long repoId, String filePath, String diff, String fileContent,
        String baseContent, Set<String> changedFilesInPr, int topK, int maxTotal, float[] vector) {

        String namespace = String.valueOf(repoId);

        StructuralInfo info = structuralInfoExtractor.extract(fileContent, filePath);

        Set<Integer> changedLines = diffLineParser.parseValidLines(diff);
        List<ChangedSymbol> changedSymbols = changedSymbolResolver.resolve(fileContent, changedLines);

        // 변경 전에는 있었는데 변경 후엔 없는 메서드 = 리네이밍된 옛 이름 또는 삭제된 메서드.
        // changedSymbols는 변경 후 파일 기준이라 새 이름만 담고 있어, 옛 이름으로
        // 호출하던 코드(컴파일이 깨지는 지점)를 놓친다. 차집합으로 그 이름을 복원한다.
        Set<String> removedSymbols = resolveRemovedSymbols(baseContent, fileContent);

        // 벡터 검색이 실패해도 구조 검색 결과로 계속 진행한다. 예전에는 여기서 예외가 올라가
        // 구조 검색 결과까지 버리고 맥락 없이 리뷰했다.
        long vectorStart = System.currentTimeMillis();
        List<SearchHit> vectorCandidates;
        try {
            vectorCandidates = vectorSearch(namespace, filePath, diff, fileContent, changedLines, vector);
        } catch (Exception e) {
            log.warn("[RAG] 벡터 검색 실패, 구조 검색 결과만 사용. filePath={}, error={}", filePath, e.toString());
            ragMetrics.fallback(RagMetrics.Stage.VECTOR_SEARCH);
            vectorCandidates = List.of();
        }
        long vectorMs = System.currentTimeMillis() - vectorStart;

        long structuralStart = System.currentTimeMillis();
        List<CalledMethodRef> addedCalls =
            addedCallResolver.resolve(fileContent, changedLines, info.calledMethodRefs(), info);
        StructuralSearchService.StructuralSearchResult structural =
            structuralSearchService.search(vector, namespace, filePath, info,
                changedSymbols, removedSymbols, addedCalls,
                resolveRemovedCalls(filePath, diff, baseContent));
        long structuralMs = System.currentTimeMillis() - structuralStart;

        // 튜닝용: 구조 검색 후보 중 최고 유사도(PR에서 바뀐 파일 제외). 구조 검색은 필터 + dense 검색이라 코사인 유사도다.
        OptionalDouble structuralTopScore = structural.candidates().stream()
            .filter(h -> !changedFilesInPr.contains(h.getString("filePath")))
            .mapToDouble(SearchHit::score)
            .max();
        structuralTopScore.ifPresent(ragMetrics::structuralTopScore);

        List<SearchHit> allCandidates =
            mergeCandidates(vectorCandidates, structural.candidates(), changedFilesInPr);

        if (allCandidates.isEmpty()) {
            log.info("[RAG-요약] file={}, 변경심볼={}, 후보=0 (vector={}, 구조={}), 시간 vector={}ms 구조={}ms",
                filePath, changedSymbols.size(), vectorCandidates.size(), structural.candidates().size(),
                vectorMs, structuralMs);
            return new SearchTrace(vectorCandidates, structural, List.of(), List.of(), List.of(), false, List.of());
        }

        List<SearchHit> structuralTop =
            structuralSearchService.selectTopStructural(
                structural, changedFilesInPr, Math.min(MAX_STRUCTURAL_SLOTS, topK));

        Set<String> structuralIds = structuralTop.stream()
            .map(SearchHit::id)
            .collect(Collectors.toSet());

        List<SearchHit> vectorOnly = allCandidates.stream()
            .filter(r -> !structuralIds.contains(r.id()))
            .toList();

        int rerankTopN = Math.min(vectorOnly.size(), maxTotal - structuralTop.size());
        String rerankQuery = rerankQueryService.buildRerankQuery(
            filePath, diff, info, changedSymbols, removedSymbols, addedCalls);
        // 관계 결합을 하려면 rerank가 고른 상위 몇 개가 아니라 후보 전체의 순위가 필요하다.
        // Cohere는 문서 수로 과금하므로 topN을 늘려도 비용은 같다.
        long rerankStart = System.currentTimeMillis();
        RerankQueryService.RerankOutcome rerankOutcome = rerankQueryService.rerankWithOutcome(
            rerankQuery, vectorOnly, structural.relationshipTags(),
            relationFusionEnabled ? vectorOnly.size() : rerankTopN);
        long rerankMs = System.currentTimeMillis() - rerankStart;
        List<SearchHit> reranked = relationFusionEnabled
            ? fuseWithRelation(rerankOutcome.hits(), structural.relationshipTags()).stream()
                .limit(rerankTopN).toList()
            : rerankOutcome.hits();

        List<SearchHit> combined = new ArrayList<>(structuralTop);
        reranked.stream()
            .filter(r -> !structuralIds.contains(r.id()))
            .forEach(combined::add);

        List<SearchHit> finalResults = diversifyFinal
            ? diversifyByClass(combined, topK)
            : combined.stream().limit(computeDynamicLimit(combined, topK, maxTotal)).toList();

        logSummary(filePath, changedSymbols.size(), vectorCandidates.size(), structural, allCandidates.size(),
            structuralIds, finalResults, rerankOutcome.fallback(), vectorMs, structuralMs, rerankMs, structuralTopScore);
        return new SearchTrace(vectorCandidates, structural, allCandidates, structuralTop,
            finalResults, rerankOutcome.fallback(), List.copyOf(combined));
    }

    /**
     * 리뷰 파일 하나의 검색 결과를 한 줄로 남긴다. 튜닝할 때 이 줄만 모아 보면 되도록 값을 한곳에 둔다.
     * 최종 결과의 출처: 슬롯 = 확정 슬롯, 구조 = 관계 태그가 있는 후보, 벡터 = 하이브리드 검색으로만 들어온 후보.
     * 리랭크 점수(top1, top5, 5위와 6위 차이)는 바로 앞의 [Rerank] 로그에 있다.
     */
    private void logSummary(String filePath, int changedSymbolCount, int vectorCount,
                            StructuralSearchService.StructuralSearchResult structural, int mergedCount,
                            Set<String> slotIds, List<SearchHit> finalResults, boolean rerankFallback,
                            long vectorMs, long structuralMs, long rerankMs, OptionalDouble structuralTopScore) {
        long fromSlot = finalResults.stream().filter(h -> slotIds.contains(h.id())).count();
        long fromStructural = finalResults.stream()
            .filter(h -> !slotIds.contains(h.id()) && structural.relationshipTags().containsKey(h.id())).count();
        long fromVector = finalResults.size() - fromSlot - fromStructural;
        String finalTags = finalResults.stream()
            .map(h -> {
                String tag = structural.relationshipTags().get(h.id());
                if (tag == null) return "벡터";
                int colon = tag.indexOf(':');
                return colon < 0 ? tag : tag.substring(0, colon);
            })
            .collect(Collectors.joining(","));

        log.info("[RAG-요약] file={}, 변경심볼={}, 후보={} (vector={}, 구조={}), 최종={} (슬롯={}, 구조={}, 벡터={}), "
                + "태그=[{}], 리랭크대체={}, 시간 vector={}ms 구조={}ms 리랭크={}ms" + ", 구조top1={}",
            filePath, changedSymbolCount, mergedCount, vectorCount, structural.candidates().size(),
            finalResults.size(), fromSlot, fromStructural, fromVector,
            finalTags, rerankFallback, vectorMs, structuralMs, rerankMs,
            structuralTopScore.isPresent() ? String.format("%.3f", structuralTopScore.getAsDouble()) : "-");
    }

    /**
     * 순위를 유지하면서 한 클래스가 MAX_PER_CLASS개를 넘게 앞자리를 차지하지 않도록 topK개를 고른다.
     * 넘친 후보는 버리지 않고 뒤로 미뤄, 다른 클래스로 자리를 채우지 못하면 순서대로 다시 채운다.
     * 클래스명이 없는 청크(슬라이딩 윈도우 등)는 서로 다른 코드이므로 중복으로 세지 않는다.
     */
    private List<SearchHit> diversifyByClass(List<SearchHit> ranked, int topK) {
        Map<String, Integer> countByClass = new HashMap<>();
        List<SearchHit> picked = new ArrayList<>();
        List<SearchHit> deferred = new ArrayList<>();

        for (SearchHit hit : ranked) {
            if (picked.size() >= topK) break;
            String cls = hit.getString("className");
            if (cls.isEmpty() || countByClass.merge(cls, 1, Integer::sum) <= MAX_PER_CLASS) {
                picked.add(hit);
            } else {
                deferred.add(hit);
            }
        }
        for (SearchHit hit : deferred) {
            if (picked.size() >= topK) break;
            picked.add(hit);
        }
        return picked;
    }

    /**
     * rerank 순위와 관계 우선순위 순위를 RRF로 합친다.
     * 관계 순위는 관계 우선순위가 같으면 rerank 순서를 따르고, 태그 없는(벡터) 후보는 맨 뒤다.
     * 점수가 아니라 순위만 쓰므로 rerank 점수와 관계 등급의 척도를 맞출 필요가 없다.
     */
    private List<SearchHit> fuseWithRelation(List<SearchHit> reranked, Map<String, String> relationshipTags) {
        Map<String, Integer> rerankRank = new HashMap<>();
        for (int i = 0; i < reranked.size(); i++) rerankRank.put(reranked.get(i).id(), i + 1);

        List<SearchHit> byRelation = new ArrayList<>(reranked);
        byRelation.sort(Comparator
            .comparingInt((SearchHit h) -> structuralSearchService.relationPriority(relationshipTags.get(h.id())))
            .thenComparingInt(h -> rerankRank.get(h.id())));
        Map<String, Integer> relationRank = new HashMap<>();
        if (relationRankByTier) {
            for (SearchHit h : reranked) {
                relationRank.put(h.id(), structuralSearchService.relationPriority(relationshipTags.get(h.id())) + 1);
            }
        } else {
            for (int i = 0; i < byRelation.size(); i++) relationRank.put(byRelation.get(i).id(), i + 1);
        }

        List<SearchHit> fused = new ArrayList<>(reranked);
        fused.sort(Comparator.comparingDouble((SearchHit h) ->
                1.0 / (RRF_K + rerankRank.get(h.id())) + 1.0 / (RRF_K + relationRank.get(h.id())))
            .reversed()
            .thenComparingInt(h -> rerankRank.get(h.id())));
        return fused;
    }

    /** 삭제된 줄의 호출. 변경 전 파일을 같은 방식으로 해석한다. base가 없으면(신규 파일) 빈 목록. */
    private List<CalledMethodRef> resolveRemovedCalls(String filePath, String diff, String baseContent) {
        if (baseContent == null || baseContent.isBlank()) return List.of();
        Set<Integer> removedLines = diffLineParser.parseRemovedLines(diff);
        if (removedLines.isEmpty()) return List.of();
        StructuralInfo baseInfo = structuralInfoExtractor.extract(baseContent, filePath);
        return addedCallResolver.resolve(baseContent, removedLines, baseInfo.calledMethodRefs(), baseInfo);
    }

    /** 변경 전/후 메서드 이름의 차집합. base가 없으면(신규 파일) 빈 집합. */
    private Set<String> resolveRemovedSymbols(String baseContent, String fileContent) {
        if (baseContent == null || baseContent.isBlank()) return Set.of();

        Set<String> beforeMethods = changedSymbolResolver.resolveMethodNames(baseContent);
        if (beforeMethods.isEmpty()) return Set.of();

        Set<String> afterMethods = changedSymbolResolver.resolveMethodNames(fileContent);
        Set<String> removed = new LinkedHashSet<>(beforeMethods);
        removed.removeAll(afterMethods);

        if (!removed.isEmpty()) {
            log.info("[RAG] 사라진 메서드 {}개: {}", removed.size(), removed);
        }
        return removed;
    }

    private List<SearchHit> mergeCandidates(
        List<SearchHit> vectorCandidates,
        List<SearchHit> metadataCandidates,
        Set<String> changedFilesInPr) {

        Set<String> seenIds = new HashSet<>();
        List<SearchHit> merged = new ArrayList<>();

        Stream.concat(vectorCandidates.stream(), metadataCandidates.stream())
            .filter(r -> !changedFilesInPr.contains(r.getString("filePath")))
            .forEach(r -> {
                if (seenIds.add(r.id())) merged.add(r);
            });

        return merged;
    }

    /**
     * 상위 후보들 중 같은 className이 여러 번 나오면, 그 중복 개수만큼 결과 총량을 늘려서
     * 다른 관련 클래스가 밀려나지 않도록 한다. base(topK)에서 시작해 max까지 확장.
     */
    private int computeDynamicLimit(List<SearchHit> ranked, int base, int max) {
        int limit = Math.min(base, ranked.size());

        while (limit < max && limit < ranked.size()) {
            List<SearchHit> window = ranked.subList(0, limit);

            Map<String, Long> countByClass = window.stream()
                .collect(Collectors.groupingBy(
                    r -> r.getString("className"), Collectors.counting()));

            long duplicateExtra = countByClass.values().stream()
                .filter(c -> c > 1).mapToLong(c -> c - 1).sum();

            int desired = Math.min(base + (int) duplicateExtra, max);
            if (desired <= limit) break;
            limit = Math.min(desired, ranked.size());
        }

        return limit;
    }

    private String formatContext(List<SearchHit> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("[관련 코드 컨텍스트]\n");

        for (SearchHit result : results) {
            String code = result.getString("code");
            if (code.isBlank()) continue;

            sb.append("// ").append(result.getString("filePath"))
                .append(" (class: ").append(result.getString("className"))
                .append(", method: ").append(result.getString("methodSignature"))
                .append(")\n");
            sb.append(code).append("\n\n");
        }

        return sb.toString().trim();
    }

    private String truncate(String text, int limit) {
        return text.length() > limit ? text.substring(0, limit) + "\n...(truncated)" : text;
    }
}