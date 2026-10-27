package project.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.core.env.Environment;
import org.springframework.web.reactive.function.client.WebClient;
import project.ai.client.QdrantClient;
import project.ai.indexing.ChunkId;
import project.ai.indexing.RepoIndexingService;
import project.ai.indexing.SparseEncoder;
import project.ai.indexing.chunking.ChunkMeta;
import project.ai.indexing.structural.StructuralInfoExtractor;
import project.ai.rag.RagContextService;
import project.ai.rag.RagContextService.SearchTrace;
import project.ai.rag.RerankQueryService;
import project.ai.rag.SearchHit;
import project.ai.rag.StructuralSearchService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 정답 데이터(PR별 리뷰에 꼭 필요한 코드)와 비교해 검색 Recall을 측정하는 수동 평가 테스트.
 * 같은 인덱스에서 추정 호출자(nested) 쿼리를 끈 A와 켠 B를 비교한다.
 *
 * 실제 Qdrant·Gemini 임베딩·Cohere를 호출하므로 -Drag.eval=true일 때만 실행한다.
 * 운영 컬렉션을 건드리지 않도록 평가 전용 컬렉션(devchat-eval)을 쓴다.
 *
 * 옵션 (모두 -D로 전달)
 *  - rag.eval.repo       : 인덱싱할 base 커밋 스냅샷 디렉터리. rag.eval.index=true일 때 필수
 *  - rag.eval.index      : true면 아직 인덱싱되지 않은 파일만 이어서 인덱싱 (기본 false)
 *  - rag.eval.reset      : true면 인덱싱 전에 평가용 저장소 청크를 모두 지움 (기본 false)
 *  - rag.eval.index-only : true면 인덱싱만 하고 평가는 건너뜀 (기본 false)
 *  - rag.eval.index-group-files : 한 번에 인덱싱할 파일 수 (기본 15, 약 임베딩 1회 분량)
 *  - rag.eval.index-delay-ms    : 인덱싱 묶음 사이 대기 시간. Gemini 호출 한도 대응 (기본 20000)
 *  - rag.eval.copy-from  : 예전 스키마(이름 없는 dense 벡터) 평가 컬렉션 이름. 지정하면 dense 벡터와
 *                          payload를 그대로 옮기고 sparse만 스냅샷에서 다시 계산한다. Gemini를 부르지 않는다.
 *                          rag.eval.repo 필요
 *  - rag.eval.prs        : 평가할 PR 번호 목록 (예: 15,18). 비우면 정답 데이터 전체
 *  - rag.eval.modes      : 실행할 모드 접두어 목록 (예: B). 비우면 A·B 모두
 *  - rag.eval.delay-ms   : 검색 요청 사이 대기 시간. Cohere 호출 한도 대응 (기본 7000)
 *  - rag.eval.collection : 평가 컬렉션 이름 (기본 devchat-eval-hybrid). 인덱싱 방식을 바꿔 비교할 때 새 이름을 준다
 *  - rag.eval.contextual-header : true면 청크 앞에 맥락 머리말을 붙여 인덱싱 (새 컬렉션에만 허용)
 *
 * Gemini 호출을 줄이려고, 쿼리 임베딩은 A·B가 함께 쓰고 build/rag-eval/query-vectors.json에
 * 저장해 다음 실행에서도 다시 부르지 않는다. 인덱싱은 파일 묶음 단위로 진행하고 이미 저장된 파일은
 * 건너뛰므로, 호출 한도로 중단돼도 다시 실행하면 이어서 진행된다.
 *
 * 데이터 (src/test/resources/rag-eval/sas/)
 *  - gt/pr{N}.json      : 정답. must(꼭 필요), related(있으면 좋음), unreachable(인덱싱 범위 밖)
 *  - pr{N}/{i}/         : PR의 변경 파일별 path.txt, pr.diff, after.java, before.java(수정 파일만)
 */
@SpringBootTest(properties = "qdrant.collection=${rag.eval.collection:devchat-eval-hybrid}")
@ActiveProfiles("local")
@EnabledIfSystemProperty(named = "rag.eval", matches = "true")
class RetrievalRecallEvaluationTest {

    private static final long REPO_ID = 900_001L;
    private static final int TOP_K = 5;

    /** 고정 K 지표(Recall@K, Hit@K, Precision@K)의 K. 동적 확장(최대 9개)을 빼고 앞 K개만 본다. */
    private static final int FIXED_K = 5;
    private static final Path DATA_DIR = Path.of("src/test/resources/rag-eval/sas");
    private static final Path REPORT_DIR = Path.of("build/rag-eval");

    @Autowired private RagContextService ragContextService;
    @Autowired private StructuralSearchService structuralSearchService;
    @Autowired private RerankQueryService rerankQueryService;
    @Autowired private RepoIndexingService repoIndexingService;
    @Autowired private QdrantClient qdrantClient;
    @Autowired private SparseEncoder sparseEncoder;
    @Autowired private Environment environment;
    @Autowired private StructuralInfoExtractor structuralInfoExtractor;
    @Autowired private project.ai.indexing.structural.AddedCallResolver addedCallResolver;

    private final ObjectMapper objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private record Mode(String name, boolean guessedCallerSearch, boolean hybridSearch, boolean relationFusion,
                        boolean diversify, boolean relationTier, boolean relationTagInDoc, boolean rerank,
                        boolean addedCall, boolean addedCallSlot) {
        Mode(String name, boolean guessedCallerSearch, boolean hybridSearch, boolean relationFusion,
             boolean diversify, boolean relationTier, boolean relationTagInDoc, boolean rerank) {
            this(name, guessedCallerSearch, hybridSearch, relationFusion, diversify, relationTier,
                relationTagInDoc, rerank, false, false);
        }
    }

    private static final List<Mode> MODES = List.of(
        new Mode("A_name_only", false, false, false, false, false, true, true),
        new Mode("B_nested_guessed", true, false, false, false, false, true, true),
        new Mode("D_hybrid_rrf", true, true, false, false, false, true, true),
        new Mode("E_relation_fusion", true, true, true, false, false, true, true),
        new Mode("F_diversify", true, true, true, true, false, true, true),
        new Mode("G_relation_tier", true, true, true, false, true, true, true),
        new Mode("H_rerank_only", true, true, false, false, false, false, true),
        // 단계별 비교용: D에서 리랭커를 끈 것(S2), 거기서 하이브리드까지 끈 것(S1)
        new Mode("S1_dense_no_rerank", true, false, false, false, false, true, false),
        new Mode("S2_hybrid_no_rerank", true, true, false, false, false, true, false),
        // G + 추가된 줄의 호출 조회(I), 거기에 확정 슬롯까지(J)
        new Mode("I_added_call", true, true, true, false, true, true, true, true, false),
        new Mode("J_added_call_slot", true, true, true, false, true, true, true, true, true),
        // 확정 슬롯은 리랭커와 무관하므로, 슬롯 내용만 볼 때는 리랭커를 끈다 (Cohere 미사용)
        new Mode("K_slot_probe", true, true, true, false, true, true, false, true, true),
        // J + 단순 메서드(getter·인터페이스 선언)는 확정 슬롯에서 제외
        new Mode("L_skip_trivial_slot", true, true, true, false, true, true, true, true, true),
        // J + 단순 메서드는 관계 순위를 뒤로 (슬롯에서도 빠짐)
        new Mode("M_demote_trivial", true, true, true, false, true, true, true, true, true),
        // M에서 관계 태그 RRF만 끔 (확정 슬롯·추가 줄 조회·단순 메서드 처리는 유지)
        new Mode("N_no_relation_rrf", true, true, false, false, false, true, true, true, true),
        // M에서 리랭크 문서의 관계 설명 줄만 뺌
        new Mode("P_no_tag_in_doc", true, true, true, false, true, false, true, true, true),
        // M + 삭제된 줄의 호출 조회 + 대상 없는 호출을 부모 클래스 메서드로 조회
        new Mode("Q_removed_inherited", true, true, true, false, true, true, true, true, true),
        // M + 대상 없는 호출(부모 클래스) + 인터페이스를 거친 호출자·구현체
        new Mode("R_inherited_interface", true, true, true, false, true, true, true, true, true),
        // R에서 인터페이스 호출자는 끄고 구현체(호출대상)만 켬
        new Mode("S_inherited_called_impl", true, true, true, false, true, true, true, true, true),
        // S + 바뀐 메서드별 벡터 질의
        new Mode("U_per_method_query", true, true, true, false, true, true, true, true, true),
        // U + 개선한 리랭크 쿼리 (diff 유지, new_calls·클래스 붙은 calls·의도 문장 추가)
        new Mode("W_rerank_v2_keep_diff", true, true, true, false, true, true, true, true, true),
        // W + 메서드 질의도 인덱싱과 같은 맥락 머리말 형식 (맥락 머리말 컬렉션과 함께 씀)
        new Mode("Y_method_query_header", true, true, true, false, true, true, true, true, true)
    );
    private static final Set<String> SKIP_TRIVIAL_MODES = Set.of("L_skip_trivial_slot");
    private static final Set<String> DEMOTE_TRIVIAL_MODES = Set.of("M_demote_trivial", "N_no_relation_rrf", "P_no_tag_in_doc", "Q_removed_inherited", "R_inherited_interface", "S_inherited_called_impl", "U_per_method_query", "W_rerank_v2_keep_diff", "Y_method_query_header");
    private static final Set<String> INHERITED_MODES = Set.of("Q_removed_inherited", "R_inherited_interface", "S_inherited_called_impl", "U_per_method_query", "W_rerank_v2_keep_diff", "Y_method_query_header");
    private static final Set<String> INTERFACE_MODES = Set.of("R_inherited_interface", "S_inherited_called_impl", "U_per_method_query", "W_rerank_v2_keep_diff", "Y_method_query_header");
    private static final Set<String> NO_INTERFACE_CALLER_MODES = Set.of("S_inherited_called_impl", "U_per_method_query", "W_rerank_v2_keep_diff", "Y_method_query_header");
    private static final Set<String> REMOVED_INHERITED_MODES = Set.of("Q_removed_inherited");

    private record ReviewFile(String path, String diff, String after, String before) {}

    /** @param mustByFile 리뷰 파일 경로 -> 그 파일 리뷰에 필요한 must (정답의 forFiles). 리뷰는 파일마다 따로 돈다. */
    private record Case(int pr, Set<String> must, Set<String> related, int unreachable,
                        Set<String> changedFiles, List<ReviewFile> files, Map<String, Set<String>> mustByFile,
                        Map<String, String> relationByKey) {}

    @Test
    void evaluateRecall() throws Exception {
        String copyFrom = System.getProperty("rag.eval.copy-from", "");
        if (!copyFrom.isBlank()) {
            copyFromLegacyCollection(copyFrom);
        }
        if (Boolean.getBoolean("rag.eval.refresh-payload")) {
            refreshPayloads();
        }
        String purgeUnder = System.getProperty("rag.eval.purge-under", "");
        if (!purgeUnder.isBlank()) {
            purgeChunksUnder(purgeUnder);
        }
        if (Boolean.getBoolean("rag.eval.index")) {
            indexIncrementally();
        }
        if (Boolean.getBoolean("rag.eval.index-only")) return;

        List<Case> cases = loadCases();
        long delayMs = Long.getLong("rag.eval.delay-ms", 7000L);
        Map<String, float[]> queryVectors = loadQueryVectors();

        Map<String, Object> report = new LinkedHashMap<>();
        Map<String, List<Map<String, Object>>> rowsByMode = new LinkedHashMap<>();

        Set<String> onlyModes = Arrays.stream(System.getProperty("rag.eval.modes", "").split(","))
            .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet());

        for (Mode mode : MODES) {
            if (!onlyModes.isEmpty() && onlyModes.stream().noneMatch(mode.name()::startsWith)) continue;
            setGuessedCallerSearch(mode.guessedCallerSearch());
            setHybridSearch(mode.hybridSearch());
            setRelationFusion(mode.relationFusion());
            setDiversify(mode.diversify());
            setRelationTier(mode.relationTier());
            setRelationTagInDoc(mode.relationTagInDoc());
            setRerank(mode.rerank());
            ReflectionTestUtils.setField((Object) AopTestUtils.getTargetObject(ragContextService), "perMethodQueryEnabled", Set.of("U_per_method_query", "W_rerank_v2_keep_diff", "Y_method_query_header").contains(mode.name()));
            ReflectionTestUtils.setField((Object) AopTestUtils.getTargetObject(rerankQueryService), "queryV2Enabled", Set.of("W_rerank_v2_keep_diff", "Y_method_query_header").contains(mode.name()));
            ReflectionTestUtils.setField((Object) AopTestUtils.getTargetObject(ragContextService), "methodQueryHeaderEnabled", "Y_method_query_header".equals(mode.name()));
            Object structuralTarget = AopTestUtils.getTargetObject(structuralSearchService);
            ReflectionTestUtils.setField(structuralTarget, "addedCallSearchEnabled", mode.addedCall());
            ReflectionTestUtils.setField(structuralTarget, "addedCallSlotEnabled", mode.addedCallSlot());
            ReflectionTestUtils.setField(structuralTarget, "addedCallSlotSkipTrivial", SKIP_TRIVIAL_MODES.contains(mode.name()));
            ReflectionTestUtils.setField(structuralTarget, "addedCallDemoteTrivial", DEMOTE_TRIVIAL_MODES.contains(mode.name()));
            ReflectionTestUtils.setField(structuralTarget, "removedCallSearchEnabled", REMOVED_INHERITED_MODES.contains(mode.name()));
            ReflectionTestUtils.setField((Object) AopTestUtils.getTargetObject(addedCallResolver), "inheritedCallEnabled", INHERITED_MODES.contains(mode.name()));
            ReflectionTestUtils.setField(structuralTarget, "interfaceRelationSearchEnabled", INTERFACE_MODES.contains(mode.name()));
            ReflectionTestUtils.setField(structuralTarget, "interfaceCallerSearchEnabled", !NO_INTERFACE_CALLER_MODES.contains(mode.name()));
            List<Map<String, Object>> rows = new ArrayList<>();

            for (Case c : cases) {
                Set<String> candidateKeys = new LinkedHashSet<>();
                Set<String> finalKeys = new LinkedHashSet<>();
                Map<String, String> tagByKey = new HashMap<>();
                int structuralCallerHits = 0;
                int structuralCallerNoise = 0;
                int rerankFallbacks = 0;
                Map<String, Integer> finalSources = new TreeMap<>();
                Set<String> topKKeys = new LinkedHashSet<>();
                int topKCount = 0;
                int topKRelevant = 0;
                int fileHits = 0;
                // 파일 단위: 그 파일에 배정된 must를 그 파일의 결과만으로 찾았는지
                List<Double> fileRecalls = new ArrayList<>();
                List<Double> fileCandidateRecalls = new ArrayList<>();
                List<Double> filePrecisions = new ArrayList<>();
                int assignedFileHits = 0;
                List<Map<String, Object>> mustDiagnostics = new ArrayList<>();
                List<Map<String, Object>> slotDiagnostics = new ArrayList<>();
                // K별 지표용: 파일마다 동적 확장 전 순위 목록의 라벨 (file-must / other-must / related / noise)
                List<Map<String, Object>> rankedByFile = new ArrayList<>();

                for (ReviewFile f : c.files()) {
                    float[] vector = queryVector(queryVectors, f);
                    SearchTrace trace = ragContextService.traceForEvaluation(
                        REPO_ID, f.path(), f.diff(), f.after(), f.before(), TOP_K, c.changedFiles(), vector);

                    if (trace.rerankFallback()) rerankFallbacks++;

                    // 진단: 확정 슬롯에 무엇이 들어갔고, 이 파일 기준으로 맞는지
                    Set<String> fileMust = c.mustByFile().getOrDefault(f.path(), Set.of());
                    for (SearchHit h : trace.structuralSlots()) {
                        String k = key(h);
                        Map<String, Object> s = new LinkedHashMap<>();
                        s.put("file", f.path());
                        s.put("slot", k);
                        s.put("tag", trace.structural().relationshipTags().get(h.id()));
                        s.put("label", fileMust.contains(k) ? "file-must" : c.must().contains(k) ? "other-file-must"
                            : c.related().contains(k) ? "related" : "noise");
                        s.put("lines", h.getString("code").lines().count());
                        slotDiagnostics.add(s);
                    }
                    trace.candidates().forEach(h -> candidateKeys.add(key(h)));
                    trace.finalResults().forEach(h -> finalKeys.add(key(h)));

                    // 동적 확장 없이 앞 K개. 동적 확장은 K개 뒤에만 덧붙이므로 앞 K개는 확장 전 결과와 같다.
                    List<String> topK = trace.finalResults().stream().limit(FIXED_K).map(RetrievalRecallEvaluationTest::key).toList();
                    topKKeys.addAll(topK);
                    topKCount += topK.size();
                    topKRelevant += (int) topK.stream().filter(k -> c.must().contains(k) || c.related().contains(k)).count();
                    if (topK.stream().anyMatch(c.must()::contains)) fileHits++;

                    List<String> rankedLabels = trace.rankedResults().stream().map(h -> {
                        String k = key(h);
                        return fileMust.contains(k) ? "file-must" : c.must().contains(k) ? "other-must"
                            : c.related().contains(k) ? "related" : "noise";
                    }).toList();
                    rankedByFile.add(Map.of("file", f.path(), "mustCount", fileMust.size(), "labels", rankedLabels));

                    Set<String> assigned = c.mustByFile().getOrDefault(f.path(), Set.of());
                    if (!assigned.isEmpty()) {
                        Set<String> fileTopK = new LinkedHashSet<>(topK);
                        Set<String> fileCandidates = trace.candidates().stream()
                            .map(RetrievalRecallEvaluationTest::key).collect(Collectors.toSet());
                        fileRecalls.add(ratio(intersect(assigned, fileTopK), assigned));
                        fileCandidateRecalls.add(ratio(intersect(assigned, fileCandidates), assigned));
                        if (!intersect(assigned, fileTopK).isEmpty()) assignedFileHits++;

                        // 진단: 배정된 must마다 어느 경로로 후보에 들어왔는지, 최종 몇 위인지
                        Map<String, String> structTags = trace.structural().relationshipTags();
                        Map<String, String> tagOfKey = new HashMap<>();
                        trace.structural().candidates().forEach(h -> tagOfKey.putIfAbsent(key(h), structTags.get(h.id())));
                        Set<String> vectorKeys = trace.vectorCandidates().stream()
                            .map(RetrievalRecallEvaluationTest::key).collect(Collectors.toSet());
                        Set<String> slotKeys = trace.structuralSlots().stream()
                            .map(RetrievalRecallEvaluationTest::key).collect(Collectors.toSet());
                        List<String> finalOrder = trace.finalResults().stream()
                            .map(RetrievalRecallEvaluationTest::key).toList();
                        List<ChunkMeta.CalledMethodRef> fileRefs =
                            structuralInfoExtractor.extract(f.after(), f.path()).calledMethodRefs();
                        for (String mustKey : assigned) {
                            String mustMethod = mustKey.substring(mustKey.lastIndexOf('#') + 1);
                            Map<String, Object> d = new LinkedHashMap<>();
                            d.put("file", f.path());
                            d.put("must", mustKey);
                            d.put("relation", c.relationByKey().get(mustKey));
                            d.put("inCandidates", fileCandidates.contains(mustKey));
                            d.put("viaVector", vectorKeys.contains(mustKey));
                            d.put("structTag", tagOfKey.get(mustKey));
                            d.put("slot", slotKeys.contains(mustKey));
                            d.put("finalRank", finalOrder.indexOf(mustKey) + 1); // 0 = 최종 결과에 없음
                            // 리뷰 파일이 이 메서드를 호출한다고 AST가 본 호출들 (힌트/확정 여부/어느 메서드에서)
                            d.put("callRefs", fileRefs.stream().filter(r -> r.methodName().equals(mustMethod))
                                .map(r -> r.targetClassHint() + "|" + r.resolved() + "|" + r.fromMethodName())
                                .distinct().toList());
                            mustDiagnostics.add(d);
                        }
                    }
                    // 파일 Precision: 이 파일 리뷰에 쓸모 있는 것 = 이 파일에 배정된 must + PR의 related
                    if (!topK.isEmpty()) {
                        filePrecisions.add((double) topK.stream()
                            .filter(k -> assigned.contains(k) || c.related().contains(k)).count() / topK.size());
                    }

                    Map<String, String> tags = trace.structural().relationshipTags();
                    for (SearchHit h : trace.structural().candidates()) {
                        String tag = tags.get(h.id());
                        if (tag == null || c.changedFiles().contains(h.getString("filePath"))) continue;
                        tagByKey.putIfAbsent(key(h), tag);
                        if (tag.startsWith("호출자")) {
                            if (c.must().contains(key(h)) || c.related().contains(key(h))) structuralCallerHits++;
                            else structuralCallerNoise++;
                        }
                    }

                    // 최종 결과가 어느 경로로 들어왔는지(확정 슬롯 / 구조 검색→rerank / 벡터 검색→rerank).
                    Set<String> slotIds = trace.structuralSlots().stream().map(SearchHit::id)
                        .collect(Collectors.toSet());
                    for (SearchHit h : trace.finalResults()) {
                        String tag = tags.get(h.id());
                        String source = slotIds.contains(h.id()) ? "SLOT"
                            : tag != null ? "STRUCT>RERANK" : "VECTOR>RERANK";
                        String label = c.must().contains(key(h)) ? "must"
                            : c.related().contains(key(h)) ? "related" : "noise";
                        finalSources.merge(source + "/" + label, 1, Integer::sum);
                        if (tag != null) {
                            finalSources.merge(source + ":" + tag.substring(0, tag.indexOf(':')) + "/" + label,
                                1, Integer::sum);
                        }
                    }
                    Thread.sleep(delayMs);
                }

                Set<String> relevant = union(c.must(), c.related());
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("pr", c.pr());
                row.put("must", c.must().size());
                row.put("related", c.related().size());
                row.put("unreachable", c.unreachable());
                row.put("mustRecallCandidates", ratio(intersect(c.must(), candidateKeys), c.must()));
                row.put("mustRecallFinal", ratio(intersect(c.must(), finalKeys), c.must()));
                row.put("relevantRecallFinal", ratio(intersect(relevant, finalKeys), relevant));
                row.put("finalCount", finalKeys.size());
                // 고정 K(동적 확장 없음). Hit는 must가 없는 PR이면 판단할 수 없으므로 null.
                row.put("recallAtK", ratio(intersect(c.must(), topKKeys), c.must()));
                row.put("precisionAtK", topKCount == 0 ? null : (double) topKRelevant / topKCount);
                row.put("files", c.files().size());
                row.put("fileHitsAtK", c.must().isEmpty() ? null : fileHits);
                row.put("fileRecallsAtK", fileRecalls);
                row.put("fileCandidateRecalls", fileCandidateRecalls);
                row.put("filePrecisionsAtK", filePrecisions);
                row.put("assignedFileHits", assignedFileHits);
                row.put("filesWithMust", fileRecalls.size());
                row.put("mustDiagnostics", mustDiagnostics);
                row.put("slotDiagnostics", slotDiagnostics);
                row.put("fileRecallAtK", fileRecalls.isEmpty() ? null
                    : fileRecalls.stream().mapToDouble(Double::doubleValue).average().orElse(0));
                row.put("rankedByFile", rankedByFile);
                row.put("prHitAtK", c.must().isEmpty() ? null : (topKKeys.stream().anyMatch(c.must()::contains) ? 1.0 : 0.0));
                row.put("finalPrecision", finalKeys.isEmpty() ? null
                    : (double) intersect(relevant, finalKeys).size() / finalKeys.size());
                row.put("structuralCallerHits", structuralCallerHits);
                row.put("structuralCallerNoise", structuralCallerNoise);
                // 0이 아니면 이 PR의 최종 결과는 리랭킹 없이 나온 것이라 해석에 주의해야 한다.
                row.put("rerankFallbacks", rerankFallbacks);
                // 파일별 최종 결과 기준(파일 간 중복 포함). 키: 경로/라벨, 경로:관계/라벨
                row.put("finalSources", finalSources);
                row.put("mustFoundInFinal", intersect(c.must(), finalKeys));
                row.put("mustMissedInCandidates", minus(c.must(), candidateKeys));
                row.put("mustTags", c.must().stream()
                    .filter(tagByKey::containsKey)
                    .collect(Collectors.toMap(k -> k, tagByKey::get, (a, b) -> a, LinkedHashMap::new)));
                row.put("finalResults", new ArrayList<>(finalKeys));
                rows.add(row);
            }
            rowsByMode.put(mode.name(), rows);
        }

        setGuessedCallerSearch(true);
        // 평가가 끝나면 운영 기본값으로 되돌린다.
        setHybridSearch(true);
        setRelationFusion(true);
        setDiversify(false);
        setRelationTier(false);
        setRelationTagInDoc(true);

        report.put("repoId", REPO_ID);
        report.put("topK", TOP_K);
        report.put("summary", summarize(rowsByMode));
        report.put("cases", rowsByMode);

        Files.createDirectories(REPORT_DIR);
        Path out = REPORT_DIR.resolve("recall-" +
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".json");
        objectMapper.writeValue(out.toFile(), report);

        printTable(rowsByMode);
        System.out.println("[RAG-EVAL] report: " + out.toAbsolutePath());
    }

    // ── 인덱싱 ──

    /**
     * 스냅샷의 특정 하위 폴더에 있는 파일의 청크를 평가 인덱스에서 지운다.
     * Windows에서 스냅샷을 풀면 docs의 심볼릭 링크(docs/modules/ROOT/examples/docs-src → docs/src)가 실제 폴더로
     * 복사돼 같은 코드가 다른 경로로 한 번 더 인덱싱된다. 운영(리눅스 클론, 링크를 따라가지 않음)에는 없는 중복이고,
     * 변경 파일 제외 필터를 피해 변경 후 코드가 후보로 새어 들어오므로 지운다.
     */
    private void purgeChunksUnder(String relativeDir) throws IOException {
        String repo = System.getProperty("rag.eval.repo");
        if (repo == null || repo.isBlank()) {
            throw new IllegalStateException("rag.eval.purge-under에는 rag.eval.repo(스냅샷 경로)가 필요합니다.");
        }
        Path repoPath = Path.of(repo);
        Path target = repoPath.resolve(relativeDir);
        int count = 0;
        try (Stream<Path> files = Files.walk(target)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                qdrantClient.deleteFileChunks(String.valueOf(REPO_ID),
                    repoPath.relativize(file).toString().replace('\\', '/'));
                count++;
            }
        }
        System.out.printf("[RAG-EVAL] %s 아래 %d개 파일의 청크 삭제%n", relativeDir, count);
    }

    /** 아직 청크가 없는 파일만 묶음 단위로 인덱싱한다. 중단돼도 다시 실행하면 이어서 진행된다. */
    private void indexIncrementally() throws Exception {
        String repo = System.getProperty("rag.eval.repo");
        if (repo == null || repo.isBlank()) {
            throw new IllegalStateException("rag.eval.index=true면 rag.eval.repo(스냅샷 경로)가 필요합니다.");
        }
        Path repoPath = Path.of(repo);
        String repoKey = String.valueOf(REPO_ID);
        String collection = environment.getRequiredProperty("qdrant.collection");
        // 테스트 JVM에는 rag.eval.* 옵션만 넘어오므로 평가 옵션으로 받아 직접 켠다.
        boolean contextual = Boolean.getBoolean("rag.eval.contextual-header");
        ReflectionTestUtils.setField((Object) AopTestUtils.getTargetObject(repoIndexingService),
            "contextualHeaderEnabled", contextual);
        System.out.printf("[RAG-EVAL] 인덱싱 컬렉션=%s, 맥락 머리말=%s%n", collection, contextual);
        // 머리말 벡터가 기존 평가 컬렉션에 섞이면 이전 측정과 비교할 수 없게 된다.
        if (contextual && "devchat-eval-hybrid".equals(collection)) {
            throw new IllegalStateException("맥락 머리말 인덱싱은 새 컬렉션(rag.eval.collection)에만 한다.");
        }
        if (Boolean.getBoolean("rag.eval.reset")) {
            qdrantClient.deleteRepo(repoKey);
        }

        List<Path> pending = new ArrayList<>();
        for (Path file : repoIndexingService.collectIndexableFiles(repoPath)) {
            String relativePath = repoPath.relativize(file).toString().replace('\\', '/');
            if (qdrantClient.listFileChunkIds(repoKey, relativePath).isEmpty()) {
                pending.add(file);
            }
        }

        int groupSize = Integer.getInteger("rag.eval.index-group-files", 15);
        long delayMs = Long.getLong("rag.eval.index-delay-ms", 20_000L);
        System.out.printf("[RAG-EVAL] 인덱싱 대상 %d개 파일, %d개씩%n", pending.size(), groupSize);

        for (int i = 0; i < pending.size(); i += groupSize) {
            List<Path> group = pending.subList(i, Math.min(i + groupSize, pending.size()));
            repoIndexingService.indexLocalFiles(REPO_ID, repoPath, group);
            System.out.printf("[RAG-EVAL] 인덱싱 %d/%d 파일 완료%n", i + group.size(), pending.size());
            if (i + groupSize < pending.size()) Thread.sleep(delayMs);
        }
    }

    // ── 쿼리 임베딩 캐시 ──

    private static final Path QUERY_CACHE = REPORT_DIR.resolve("query-vectors.json");

    private Map<String, float[]> loadQueryVectors() throws IOException {
        if (!Files.exists(QUERY_CACHE)) return new HashMap<>();
        return objectMapper.readValue(QUERY_CACHE.toFile(),
            objectMapper.getTypeFactory().constructMapType(HashMap.class, String.class, float[].class));
    }

    /** 같은 파일·diff면 저장된 벡터를 쓰고, 없을 때만 임베딩해서 바로 저장한다. */
    private float[] queryVector(Map<String, float[]> cache, ReviewFile f) throws IOException {
        String cacheKey = f.path() + "@" + Integer.toHexString(f.diff().hashCode());
        float[] vector = cache.get(cacheKey);
        if (vector == null) {
            vector = ragContextService.embedQuery(f.path(), f.diff());
            cache.put(cacheKey, vector);
            Files.createDirectories(REPORT_DIR);
            objectMapper.writeValue(QUERY_CACHE.toFile(), cache);
        }
        return vector;
    }

    private void setGuessedCallerSearch(boolean enabled) {
        Object target = AopTestUtils.getTargetObject(structuralSearchService);
        ReflectionTestUtils.setField(target, "guessedCallerSearchEnabled", enabled);
    }

    private void setHybridSearch(boolean enabled) {
        Object target = AopTestUtils.getTargetObject(ragContextService);
        ReflectionTestUtils.setField(target, "hybridSearchEnabled", enabled);
    }

    private void setRelationFusion(boolean enabled) {
        Object target = AopTestUtils.getTargetObject(ragContextService);
        ReflectionTestUtils.setField(target, "relationFusionEnabled", enabled);
    }

    private void setDiversify(boolean enabled) {
        Object target = AopTestUtils.getTargetObject(ragContextService);
        ReflectionTestUtils.setField(target, "diversifyFinal", enabled);
    }

    private void setRelationTier(boolean enabled) {
        Object target = AopTestUtils.getTargetObject(ragContextService);
        ReflectionTestUtils.setField(target, "relationRankByTier", enabled);
    }

    private void setRelationTagInDoc(boolean enabled) {
        Object target = AopTestUtils.getTargetObject(rerankQueryService);
        ReflectionTestUtils.setField(target, "includeRelationTag", enabled);
    }

    private void setRerank(boolean enabled) {
        Object target = AopTestUtils.getTargetObject(rerankQueryService);
        ReflectionTestUtils.setField(target, "rerankEnabled", enabled);
    }

    // ── payload 갱신 ──

    /**
     * 추출 규칙(클래스 이름 정규화 등)이 바뀌었을 때, 벡터는 그대로 두고 payload만 다시 계산해 덮어쓴다.
     * 임베딩은 청크 본문으로만 만들어지므로 추출 규칙이 바뀌어도 그대로 유효하다. Gemini를 부르지 않는다.
     */
    private void refreshPayloads() throws IOException {
        String repo = System.getProperty("rag.eval.repo");
        if (repo == null || repo.isBlank()) {
            throw new IllegalStateException("rag.eval.refresh-payload에는 rag.eval.repo(스냅샷 경로)가 필요합니다.");
        }
        Map<String, ChunkMeta> chunkById = rechunkSnapshot(Path.of(repo));
        String collection = environment.getRequiredProperty("qdrant.collection");

        int refreshed = 0;
        int missing = 0;
        Object offset = null;
        do {
            JsonNode page = scroll(collection, offset);
            if (page == null) break;

            List<QdrantClient.Point> points = new ArrayList<>();
            for (JsonNode p : page.path("result").path("points")) {
                String id = p.path("id").asText();
                ChunkMeta meta = chunkById.get(id);
                if (meta == null) {
                    missing++;
                    continue;
                }
                float[] dense = objectMapper.convertValue(p.path("vector").path("dense"), float[].class);
                points.add(new QdrantClient.Point(id, dense, sparseEncoder.encodeDocument(meta.chunk()),
                    repoIndexingService.payloadOf(REPO_ID, meta)));
            }
            qdrantClient.upsert(points);
            refreshed += points.size();
            offset = nextOffset(page);
        } while (offset != null);

        System.out.printf("[RAG-EVAL] payload 갱신 %d개 (스냅샷에서 청크를 못 찾아 건너뜀: %d개)%n", refreshed, missing);
    }

    /** 스냅샷을 인덱싱과 같은 규칙으로 청킹해 청크 ID → 청크(인덱싱 정보 포함). */
    private Map<String, ChunkMeta> rechunkSnapshot(Path repoPath) throws IOException {
        Map<String, ChunkMeta> chunkById = new HashMap<>();
        for (Path file : repoIndexingService.collectIndexableFiles(repoPath)) {
            String relativePath = repoPath.relativize(file).toString().replace('\\', '/');
            String content = Files.readString(file);
            if (content.isBlank()) continue;
            List<ChunkMeta> chunks = repoIndexingService.chunkForIndexing(content, relativePath);
            for (int i = 0; i < chunks.size(); i++) {
                String id = ChunkId.of(REPO_ID, relativePath, i);
                chunkById.put(id, chunks.get(i).withIndexingInfo(id, relativePath, i));
            }
        }
        return chunkById;
    }

    private JsonNode scroll(String collection, Object offset) {
        Map<String, Object> body = new HashMap<>();
        body.put("filter", Map.of("must", List.of(
            Map.of("key", "repoId", "match", Map.of("value", String.valueOf(REPO_ID))))));
        body.put("limit", 128);
        body.put("with_payload", false);
        body.put("with_vector", List.of("dense"));
        if (offset != null) body.put("offset", offset);
        return qdrantRest().post()
            .uri("/collections/{c}/points/scroll", collection)
            .bodyValue(body)
            .retrieve()
            .bodyToMono(JsonNode.class)
            .block();
    }

    private static Object nextOffset(JsonNode page) {
        JsonNode next = page.path("result").path("next_page_offset");
        return next.isMissingNode() || next.isNull() ? null : next.asText();
    }

    private WebClient qdrantRest() {
        return WebClient.builder()
            .baseUrl(environment.getRequiredProperty("qdrant.url"))
            .defaultHeader("api-key", environment.getRequiredProperty("qdrant.api-key"))
            .codecs(c -> c.defaultCodecs().maxInMemorySize(64 * 1024 * 1024))
            .build();
    }

    // ── 예전 스키마 컬렉션에서 복사 ──

    /**
     * 예전 평가 컬렉션의 dense 벡터와 payload를 그대로 옮기고, sparse 벡터만 새로 계산해 넣는다.
     * sparse는 payload의 code(2500자에서 잘림)가 아니라 스냅샷을 인덱싱과 같은 규칙으로 다시 청킹한
     * 전문으로 만든다. 청크 ID가 (저장소, 경로, 순번)으로 결정되므로 다시 청킹해도 같은 ID가 나온다.
     */
    private void copyFromLegacyCollection(String source) throws IOException {
        String repo = System.getProperty("rag.eval.repo");
        if (repo == null || repo.isBlank()) {
            throw new IllegalStateException("rag.eval.copy-from에는 rag.eval.repo(스냅샷 경로)가 필요합니다.");
        }
        Path repoPath = Path.of(repo);

        Map<String, String> chunkTextById = new HashMap<>();
        for (Path file : repoIndexingService.collectIndexableFiles(repoPath)) {
            String relativePath = repoPath.relativize(file).toString().replace('\\', '/');
            String content = Files.readString(file);
            if (content.isBlank()) continue;
            List<ChunkMeta> chunks = repoIndexingService.chunkForIndexing(content, relativePath);
            for (int i = 0; i < chunks.size(); i++) {
                chunkTextById.put(ChunkId.of(REPO_ID, relativePath, i), chunks.get(i).chunk());
            }
        }

        WebClient legacy = WebClient.builder()
            .baseUrl(environment.getRequiredProperty("qdrant.url"))
            .defaultHeader("api-key", environment.getRequiredProperty("qdrant.api-key"))
            .codecs(c -> c.defaultCodecs().maxInMemorySize(64 * 1024 * 1024))
            .build();

        int copied = 0;
        int missingText = 0;
        Object offset = null;
        do {
            Map<String, Object> body = new HashMap<>();
            body.put("filter", Map.of("must", List.of(
                Map.of("key", "repoId", "match", Map.of("value", String.valueOf(REPO_ID))))));
            body.put("limit", 128);
            body.put("with_payload", true);
            body.put("with_vector", true);
            if (offset != null) body.put("offset", offset);

            JsonNode page = legacy.post()
                .uri("/collections/{c}/points/scroll", source)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
            if (page == null) break;

            List<QdrantClient.Point> points = new ArrayList<>();
            for (JsonNode p : page.path("result").path("points")) {
                String id = p.path("id").asText();
                float[] dense = objectMapper.convertValue(p.path("vector"), float[].class);
                Map<String, Object> payload = objectMapper.convertValue(p.path("payload"),
                    objectMapper.getTypeFactory().constructMapType(HashMap.class, String.class, Object.class));
                String text = chunkTextById.get(id);
                if (text == null) {
                    missingText++;
                    text = String.valueOf(payload.getOrDefault("code", ""));
                }
                points.add(new QdrantClient.Point(id, dense, sparseEncoder.encodeDocument(text), payload));
            }
            qdrantClient.upsert(points);
            copied += points.size();

            JsonNode next = page.path("result").path("next_page_offset");
            offset = next.isMissingNode() || next.isNull() ? null : next.asText();
        } while (offset != null);

        System.out.printf("[RAG-EVAL] %s에서 %d개 복사 (전문을 못 찾아 payload code로 sparse 계산: %d개)%n",
            source, copied, missingText);
    }

    // ── 데이터 로드 ──

    private List<Case> loadCases() throws IOException {
        Set<Integer> only = parsePrFilter(System.getProperty("rag.eval.prs", ""));
        List<Case> cases = new ArrayList<>();

        try (Stream<Path> gts = Files.list(DATA_DIR.resolve("gt"))) {
            for (Path gtFile : gts.sorted().toList()) {
                JsonNode gt = objectMapper.readTree(gtFile.toFile());
                int pr = gt.get("pr").asInt();
                if (!only.isEmpty() && !only.contains(pr)) continue;

                List<ReviewFile> files = loadReviewFiles(DATA_DIR.resolve("pr" + pr));
                Set<String> changed = new HashSet<>();
                files.forEach(f -> changed.add(f.path()));
                gt.path("changedJavaFiles").forEach(n -> changed.add(n.asText()));

                Map<String, Set<String>> mustByFile = new HashMap<>();
                gt.path("must").forEach(n -> n.path("forFiles").forEach(file ->
                    mustByFile.computeIfAbsent(file.asText(), k -> new LinkedHashSet<>()).add(
                        key(n.path("filePath").asText(), n.path("className").asText(), n.path("methodName").asText()))));

                Map<String, String> relationByKey = new HashMap<>();
                gt.path("must").forEach(n -> relationByKey.put(
                    key(n.path("filePath").asText(), n.path("className").asText(), n.path("methodName").asText()),
                    n.path("relation").asText()));

                cases.add(new Case(pr, keys(gt.path("must")), keys(gt.path("related")),
                    gt.path("unreachable").size(), changed, files, mustByFile, relationByKey));
            }
        }
        cases.sort(Comparator.comparingInt(Case::pr));
        return cases;
    }

    private List<ReviewFile> loadReviewFiles(Path prDir) throws IOException {
        List<ReviewFile> files = new ArrayList<>();
        try (Stream<Path> dirs = Files.list(prDir)) {
            for (Path dir : dirs.sorted().toList()) {
                Path before = dir.resolve("before.java");
                files.add(new ReviewFile(
                    Files.readString(dir.resolve("path.txt")).trim(),
                    Files.readString(dir.resolve("pr.diff")),
                    Files.readString(dir.resolve("after.java")),
                    Files.exists(before) ? Files.readString(before) : ""));
            }
        }
        return files;
    }

    private Set<Integer> parsePrFilter(String value) {
        return Arrays.stream(value.split(","))
            .map(String::trim).filter(s -> !s.isEmpty())
            .map(Integer::parseInt).collect(Collectors.toSet());
    }

    // ── 키·집합 ──

    /** 청크 식별 키. 같은 메서드의 오버로드는 하나로 본다. */
    private static String key(String filePath, String className, String methodName) {
        return filePath + "#" + className + "#" + methodName;
    }

    private static String key(SearchHit h) {
        return key(h.getString("filePath"), h.getString("className"), h.getString("methodName"));
    }

    private static Set<String> keys(JsonNode items) {
        Set<String> keys = new LinkedHashSet<>();
        items.forEach(n -> keys.add(key(n.path("filePath").asText(), n.path("className").asText(),
            n.path("methodName").asText())));
        return keys;
    }

    private static Set<String> union(Set<String> a, Set<String> b) {
        Set<String> r = new LinkedHashSet<>(a);
        r.addAll(b);
        return r;
    }

    private static Set<String> intersect(Set<String> a, Set<String> b) {
        Set<String> r = new LinkedHashSet<>(a);
        r.retainAll(b);
        return r;
    }

    private static Set<String> minus(Set<String> a, Set<String> b) {
        Set<String> r = new LinkedHashSet<>(a);
        r.removeAll(b);
        return r;
    }

    /** 분모가 0이면(정답 없음) 평균에서 빼도록 null. */
    private static Double ratio(Set<String> hit, Set<String> all) {
        return all.isEmpty() ? null : (double) hit.size() / all.size();
    }

    // ── 리포트 ──

    private Map<String, Object> summarize(Map<String, List<Map<String, Object>>> rowsByMode) {
        Map<String, Object> summary = new LinkedHashMap<>();
        rowsByMode.forEach((mode, rows) -> {
            Map<String, Object> s = new LinkedHashMap<>();
            // 파일 Hit@K는 PR 평균이 아니라 파일 전체 기준(파일 수가 많은 PR이 그만큼 반영된다).
            int hitFiles = rows.stream().filter(r -> r.get("fileHitsAtK") != null)
                .mapToInt(r -> (int) r.get("fileHitsAtK")).sum();
            int evaluatedFiles = rows.stream().filter(r -> r.get("fileHitsAtK") != null)
                .mapToInt(r -> (int) r.get("files")).sum();
            s.put("fileHitRateAtK", evaluatedFiles == 0 ? Double.NaN : (double) hitFiles / evaluatedFiles);

            // 파일 단위 지표: PR 평균이 아니라 must가 배정된 리뷰 파일 전체의 평균 (리뷰 1회 = 파일 1개)
            for (String[] m : List.of(
                new String[]{"fileRecallsAtK", "perFileRecallAtK"},
                new String[]{"fileCandidateRecalls", "perFileCandidateRecall"},
                new String[]{"filePrecisionsAtK", "perFilePrecisionAtK"})) {
                s.put(m[1], rows.stream().flatMap(r -> ((List<Double>) r.get(m[0])).stream())
                    .mapToDouble(Double::doubleValue).average().orElse(Double.NaN));
            }
            int filesWithMust = rows.stream().mapToInt(r -> (int) r.get("filesWithMust")).sum();
            s.put("filesWithMust", filesWithMust);
            s.put("perFileHitAtK", filesWithMust == 0 ? Double.NaN
                : (double) rows.stream().mapToInt(r -> (int) r.get("assignedFileHits")).sum() / filesWithMust);
            for (String metric : List.of("mustRecallCandidates", "mustRecallFinal",
                "relevantRecallFinal", "finalPrecision", "recallAtK", "precisionAtK", "prHitAtK")) {
                s.put(metric, rows.stream().map(r -> (Double) r.get(metric))
                    .filter(Objects::nonNull).mapToDouble(Double::doubleValue).average().orElse(Double.NaN));
            }
            s.put("structuralCallerHits", rows.stream().mapToInt(r -> (int) r.get("structuralCallerHits")).sum());
            s.put("structuralCallerNoise", rows.stream().mapToInt(r -> (int) r.get("structuralCallerNoise")).sum());
            s.put("rerankFallbacks", rows.stream().mapToInt(r -> (int) r.get("rerankFallbacks")).sum());
            Map<String, Integer> sources = new TreeMap<>();
            rows.forEach(r -> ((Map<String, Integer>) r.get("finalSources"))
                .forEach((k, v) -> sources.merge(k, v, Integer::sum)));
            s.put("finalSources", sources);
            summary.put(mode, s);
        });
        return summary;
    }

    private void printTable(Map<String, List<Map<String, Object>>> rowsByMode) {
        System.out.println("=================== RAG Recall ===================");
        rowsByMode.forEach((mode, rows) -> {
            System.out.println("[" + mode + "]");
            System.out.printf("%-5s %5s %8s %8s %9s %6s %8s %8s %9s %5s%n",
                "PR", "must", "R@cand", "R@5", "fileR@5", "P@5", "fileHit", "prHit", "R@final*", "fb");
            for (Map<String, Object> r : rows) {
                System.out.printf("#%-4s %5s %8s %8s %9s %6s %8s %8s %9s %5s%n",
                    r.get("pr"), r.get("must"), pct(r.get("mustRecallCandidates")),
                    pct(r.get("recallAtK")), pct(r.get("fileRecallAtK")), pct(r.get("precisionAtK")),
                    r.get("fileHitsAtK") == null ? "-" : r.get("fileHitsAtK") + "/" + r.get("files"),
                    pct(r.get("prHitAtK")), pct(r.get("mustRecallFinal")), r.get("rerankFallbacks"));
            }
            System.out.println("(*R@final: 동적 확장 포함, 파일당 5~9개)");
        });
        System.out.println("==================================================");
    }

    private static String pct(Object v) {
        return v == null ? "-" : String.format("%.0f%%", (Double) v * 100);
    }
}
