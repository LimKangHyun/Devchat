package project.ai.service;

import com.google.protobuf.Struct;
import io.pinecone.unsigned_indices_model.ScoredVectorWithUnsignedIndices;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import project.ai.client.PineconeClient;
import project.ai.service.chunker.AstChunkExtractor;
import project.ai.service.chunker.ChunkMeta;
import project.ai.service.chunker.ChunkMeta.CalledMethodRef;
import project.common.exception.ex.IndexingException;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagContextService {

    private final EmbeddingService embeddingService;
    private final PineconeClient pineconeClient;
    private final CohereRerankService cohereRerankService;
    private final AstChunkExtractor astChunkExtractor;

    private static final int TOP_K = 5;
    private static final int MAX_TOTAL_RESULTS = 9;   // 동일 클래스 중복 보정 시 최대 상한
    private static final int MAX_STRUCTURAL_SLOTS = 3;
    private static final int VECTOR_CANDIDATE_SIZE = 30;
    private static final int METADATA_CANDIDATE_SIZE = 10;

    // ── getter/setter 및 흔한 Object 메서드 판별 (구조 검색 노이즈 제거용) ──
    private static final Pattern GETTER_PATTERN = Pattern.compile("^(get|is)[A-Z0-9].*");
    private static final Pattern SETTER_PATTERN = Pattern.compile("^set[A-Z0-9].*");
    private static final Set<String> COMMON_OBJECT_METHODS = Set.of(
        "toString", "hashCode", "equals", "clone", "notify", "notifyAll", "wait", "getClass",
        "build", "builder", "of", "from"
    );

    public String buildContext(Long repoId, String filePath, String diff,
        String fileContent, Set<String> changedFilesInPr) {
        try {
            String embeddingInput = "File: " + filePath + "\nDiff:\n" + truncate(diff, 2000);
            float[] vector = embeddingService.embed(embeddingInput);
            String namespace = String.valueOf(repoId);

            // 1. 구조 정보 추출
            StructuralInfo info = extractStructuralInfo(fileContent, filePath);

            // 2. Vector search — 의미적 유사도 기반 후보
            List<ScoredVectorWithUnsignedIndices> vectorCandidates =
                pineconeClient.query(vector, VECTOR_CANDIDATE_SIZE, namespace);

            // 3. Metadata search — 구조적 관계 기반 후보
            StructuralSearchResult structuralResult =
                searchByStructure(vector, namespace, filePath, info);

            // 4. 합치고 중복 제거 + 변경 파일 제외
            List<ScoredVectorWithUnsignedIndices> allCandidates =
                mergeCandidates(vectorCandidates, structuralResult.candidates(), changedFilesInPr);

            if (allCandidates.isEmpty()) {
                log.info("[RAG] repoId={}, filePath={}, 검색결과 없음", repoId, filePath);
                return "";
            }

            log.info("[RAG] 후보 수: vector={}, metadata={}, 합산(중복제거)={}",
                vectorCandidates.size(), structuralResult.candidates().size(), allCandidates.size());

            // 5. 구조적 결과는 관계 우선순위 + 클래스 다양성으로 선택 (rerank 안 거침)
            List<ScoredVectorWithUnsignedIndices> structuralTop =
                selectTopStructural(structuralResult, changedFilesInPr, MAX_STRUCTURAL_SLOTS);

            // 6. 나머지 후보에서 구조적 결과 제외 후 Cohere rerank
            Set<String> structuralIds = structuralTop.stream()
                .map(ScoredVectorWithUnsignedIndices::getId)
                .collect(Collectors.toSet());

            List<ScoredVectorWithUnsignedIndices> vectorOnly = allCandidates.stream()
                .filter(r -> !structuralIds.contains(r.getId()))
                .toList();

            // structural이 채운 슬롯을 뺀 만큼 rerank pool을 넉넉히 확보 (동적 확장의 재료)
            int rerankTopN = Math.min(vectorOnly.size(), MAX_TOTAL_RESULTS - structuralTop.size());

            String rerankQuery = buildRerankQuery(filePath, diff, info);
            List<ScoredVectorWithUnsignedIndices> rerankedVector =
                rerankCandidates(rerankQuery, vectorOnly, structuralResult.relationshipTags(), rerankTopN);

            // 7. 합치기: 구조적(최대 3개) + 의미적(나머지) — 우선순위 순서로 하나의 랭킹 리스트 구성
            List<ScoredVectorWithUnsignedIndices> combinedRanked = new ArrayList<>(structuralTop);
            rerankedVector.stream()
                .filter(r -> !structuralIds.contains(r.getId()))
                .forEach(combinedRanked::add);

            // 동일 클래스 중복이 있으면 그만큼 총량을 늘려서(최대 9개) 다른 클래스가 밀려나지 않게 함
            int dynamicLimit = computeDynamicLimit(combinedRanked, TOP_K, MAX_TOTAL_RESULTS);
            List<ScoredVectorWithUnsignedIndices> results = combinedRanked.stream()
                .limit(dynamicLimit)
                .toList();

            for (ScoredVectorWithUnsignedIndices r : results) {
                log.info("[RAG] reviewFile={}, candidatePath={}, class={}, method={}",
                    filePath, getStringField(r, "filePath"),
                    getStringField(r, "className"), getStringField(r, "methodSignature"));
            }

            return formatContext(results);

        } catch (IndexingException e) {
            log.debug("RAG 임베딩 실패, RAG 없이 리뷰 진행. repoId={}, filePath={}", repoId, filePath);
            return "";
        } catch (Exception e) {
            log.warn("RAG 컨텍스트 조회 중 예상치 못한 오류. repoId={}, filePath={}", repoId, filePath, e);
            return "";
        }
    }

    // ── 구조적 검색 결과 + 관계 태그 ──

    private record StructuralSearchResult(
        List<ScoredVectorWithUnsignedIndices> candidates,
        Map<String, String> relationshipTags
    ) {
        static final StructuralSearchResult EMPTY =
            new StructuralSearchResult(List.of(), Map.of());
    }

    private StructuralSearchResult searchByStructure(
        float[] vector, String namespace, String filePath, StructuralInfo info) {

        try {
            if (info.isEmpty()) return StructuralSearchResult.EMPTY;

            Set<String> seenIds = new HashSet<>();
            List<ScoredVectorWithUnsignedIndices> results = new ArrayList<>();
            Map<String, String> tags = new HashMap<>();

            if (info.superClassName() != null) {
                addFilteredResults(results, seenIds, tags, vector, namespace,
                    "superClassName", info.superClassName(),
                    "형제(상속): 같은 부모 " + info.superClassName() + "를 상속");
            }

            for (String iface : info.interfaceNames()) {
                addFilteredResults(results, seenIds, tags, vector, namespace,
                    "interfaceNames", iface,
                    "형제(구현): 같은 인터페이스 " + iface + "를 구현");
            }

            if (info.className() != null) {
                addFilteredResults(results, seenIds, tags, vector, namespace,
                    "interfaceNames", info.className(),
                    "구현체: " + info.className() + "를 구현하는 클래스");
                addFilteredResults(results, seenIds, tags, vector, namespace,
                    "superClassName", info.className(),
                    "자식클래스: " + info.className() + "를 상속하는 클래스");
                addFilteredResults(results, seenIds, tags, vector, namespace,
                    "referencedTypeNames", info.className(),
                    "참조자: " + info.className() + "를 참조하는 코드");

                // [변경] 이름만 매칭 → "클래스.메서드" 조합으로 정밀 매칭
                for (String methodName : info.methodNames().stream()
                    .filter(this::isMeaningfulMethodName)
                    .limit(3)
                    .toList()) {
                    addFilteredResults(results, seenIds, tags, vector, namespace,
                        "calledMethodQualified", info.className() + "." + methodName,
                        "호출자: " + info.className() + "." + methodName + "()를 호출하는 코드");
                }
                // 폴백: 관례 추정이 빗나가 정밀 매칭이 0건일 수 있으므로 이름만 매칭도 유지한다.
                // 정밀에서 이미 잡힌 청크는 seenIds가 걸러주므로 중복되지 않는다.
                addFilteredResults(results, seenIds, tags, vector, namespace,
                    "calledMethodNames", info.methodNames(),
                    "호출자(이름만): 변경된 클래스의 메서드를 호출하는 코드");
            }

            for (String type : info.referencedTypeNames()) {
                addFilteredResults(results, seenIds, tags, vector, namespace,
                    "className", type,
                    "사용타입: 변경 코드가 " + type + "을 사용");
            }

            List<CalledMethodRef> meaningfulCalls = info.calledMethodRefs().stream()
                .filter(ref -> isMeaningfulMethodName(ref.methodName()))
                .limit(5)
                .toList();

            for (CalledMethodRef ref : meaningfulCalls) {
                if (ref.targetClassHint() != null) {
                    addFilteredResultsCompound(results, seenIds, tags, vector, namespace,
                        "className", ref.targetClassHint(), "methodName", ref.methodName(),
                        "사용호출: 변경 코드가 " + ref.targetClassHint() + "." + ref.methodName() + "()를 호출");
                } else {
                    addFilteredResults(results, seenIds, tags, vector, namespace,
                        "methodName", ref.methodName(),
                        "사용호출: 변경 코드가 " + ref.methodName() + "()를 호출 (클래스 특정 불가)");
                }
            }

            log.info("[RAG-구조] filePath={}, superClass={}, interfaces={}, 구조적 후보={}개",
                filePath, info.superClassName(), info.interfaceNames(), results.size());

            return new StructuralSearchResult(results, tags);

        } catch (Exception e) {
            log.warn("[RAG-구조] 구조적 검색 실패, vector search만 사용. filePath={}", filePath, e);
            return StructuralSearchResult.EMPTY;
        }
    }

    // ── 구조적 결과 우선순위 선택 (클래스당 최대 1개로 다양성 확보) ──

    private List<ScoredVectorWithUnsignedIndices> selectTopStructural(
            StructuralSearchResult result, Set<String> changedFilesInPr, int limit) {

            // 변경으로 "깨질 수 있는" 쪽(변경된 파일을 쓰는 코드)을 먼저 가져온다.
            // 인터페이스/부모 시그니처가 바뀌면 구현체·상속체가 컴파일 에러가 나므로 리뷰에 필수.
            // 반대로 "변경 코드가 쓰는 타입/메서드"는 이 PR에서 안 바뀌므로 깨지지 않는다(맥락용).
            // 주의: 아래 문자열은 searchByStructure()가 붙이는 태그와 contains로 매칭되므로
            //       태그 문구를 수정하면 여기도 함께 고쳐야 한다.
        List<String> priorityOrder = List.of(
            "구현체:",          // 내 인터페이스를 구현 — 시그니처 변경 시 컴파일 에러
            "자식클래스:",      // 나를 상속 — 시그니처 변경 시 컴파일 에러
            "호출자:",          // 내 메서드를 호출 (클래스까지 확인됨) — 컴파일 에러
            "참조자:",          // 나를 타입으로 참조 — 타입 변경 시 영향
            "호출자(이름만):",  // 내 메서드를 호출 (동명이인 가능) — 폴백
            "형제(구현):",      // 같은 인터페이스 구현 — 참고용
            "형제(상속):",      // 같은 부모 상속 — 참고용
            "사용타입:",        // 맥락용
            "사용호출:"         // 맥락용
        );

        List<ScoredVectorWithUnsignedIndices> sorted = new ArrayList<>(result.candidates());

        sorted.sort((a, b) -> {
            String tagA = result.relationshipTags().getOrDefault(a.getId(), "");
            String tagB = result.relationshipTags().getOrDefault(b.getId(), "");
            int priorityCompare = Integer.compare(
                getPriority(tagA, priorityOrder),
                getPriority(tagB, priorityOrder));
            if (priorityCompare != 0) return priorityCompare;
            // 같은 우선순위면 벡터 유사도 높은 순.
            // 없으면 "어느 필터가 먼저 실행됐나"라는 무의미한 기준으로 결정된다.
            return Float.compare(b.getScore(), a.getScore());
        });

        // 같은 className이 슬롯을 독점하지 못하게 클래스당 1개까지만 채택.
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

    private int getPriority(String tag, List<String> priorityOrder) {
        for (int i = 0; i < priorityOrder.size(); i++) {
            if (tag.contains(priorityOrder.get(i))) return i;
        }
        return priorityOrder.size();
    }

    // ── Rerank ──

    private List<ScoredVectorWithUnsignedIndices> rerankCandidates(
        String query, List<ScoredVectorWithUnsignedIndices> candidates,
        Map<String, String> relationshipTags, int topN) {

        List<String> documents = candidates.stream()
            .map(r -> buildRerankDocument(r, relationshipTags))
            .toList();

        List<CohereRerankService.RerankResult> rerankResults =
            cohereRerankService.rerank(query, documents, topN);

        if (rerankResults.isEmpty()) return candidates;

        return rerankResults.stream()
            .map(r -> candidates.get(r.index()))
            .toList();
    }

    private String buildRerankDocument(ScoredVectorWithUnsignedIndices result,
        Map<String, String> relationshipTags) {
        String className = getStringField(result, "className");
        String superClassName = getStringField(result, "superClassName");
        String methodSignature = getStringField(result, "methodSignature");
        String packageName = getStringField(result, "packageName");
        List<String> interfaceNames = getListField(result, "interfaceNames");
        List<String> calledMethodNames = getListField(result, "calledMethodNames");
        List<String> referencedTypeNames = getListField(result, "referencedTypeNames");
        List<String> annotations = getListField(result, "annotations");
        String code = getStringField(result, "code");

        StringBuilder sb = new StringBuilder();

        String tag = relationshipTags.get(result.getId());
        if (tag != null) {
            sb.append("[관계: ").append(tag).append("]\n");
        }

        sb.append("package: ").append(packageName).append("\n");
        sb.append("class: ").append(className);
        if (!superClassName.isEmpty()) sb.append(" extends ").append(superClassName);
        if (!interfaceNames.isEmpty()) sb.append(" implements ").append(String.join(", ", interfaceNames));
        sb.append("\n");
        sb.append("method: ").append(methodSignature).append("\n");
        if (!annotations.isEmpty()) sb.append("annotations: ").append(String.join(", ", annotations)).append("\n");
        if (!calledMethodNames.isEmpty()) sb.append("calls: ").append(String.join(", ", calledMethodNames)).append("\n");
        if (!referencedTypeNames.isEmpty()) sb.append("references: ").append(String.join(", ", referencedTypeNames)).append("\n");
        sb.append("code:\n").append(code);

        return sb.toString();
    }

    // ── Rerank Query ──

    private String buildRerankQuery(String filePath, String diff, StructuralInfo info) {
        StringBuilder sb = new StringBuilder();
        sb.append("변경 파일: ").append(filePath).append("\n");

        if (info.className() != null) {
            sb.append("변경 클래스: ").append(info.className()).append("\n");
        }
        if (info.superClassName() != null) {
            sb.append("상속: ").append(info.superClassName()).append("\n");
        }
        if (!info.interfaceNames().isEmpty()) {
            sb.append("구현: ").append(String.join(", ", info.interfaceNames())).append("\n");
        }
        if (!info.referencedTypeNames().isEmpty()) {
            sb.append("사용하는 타입: ").append(String.join(", ", info.referencedTypeNames())).append("\n");
        }
        if (!info.calledMethodNames().isEmpty()) {
            // [변경] length()>4 기준 → isMeaningfulMethodName으로 통일 (getter/setter 정규식 판별)
            List<String> meaningful = info.calledMethodNames().stream()
                .filter(this::isMeaningfulMethodName)
                .limit(10)
                .toList();
            if (!meaningful.isEmpty()) {
                sb.append("호출하는 메서드: ").append(String.join(", ", meaningful)).append("\n");
            }
        }
        sb.append("이 변경이 영향을 줄 수 있는 호출 관계, 상속 관계, 타입 참조 코드를 찾아야 함.\n");
        sb.append("Diff:\n").append(truncate(diff, 1000));

        return sb.toString();
    }

    // ── 구조 정보 추출 ──

    private StructuralInfo extractStructuralInfo(String fileContent, String filePath) {
        if (fileContent == null || fileContent.isBlank()) return StructuralInfo.EMPTY;

        List<ChunkMeta> chunks = astChunkExtractor.extract(fileContent, filePath);
        if (chunks.isEmpty()) return StructuralInfo.EMPTY;

        String className = null;
        String superClassName = null;
        Set<String> interfaceNames = new LinkedHashSet<>();
        Set<String> methodNames = new LinkedHashSet<>();
        Set<String> referencedTypes = new LinkedHashSet<>();
        Set<String> calledMethods = new LinkedHashSet<>();
        Set<CalledMethodRef> calledMethodRefs = new LinkedHashSet<>(); // [추가]

        for (ChunkMeta chunk : chunks) {
            if (chunk.className() != null && !chunk.className().isEmpty()) {
                className = chunk.className();
            }
            if (chunk.superClassName() != null && !chunk.superClassName().isEmpty()) {
                superClassName = chunk.superClassName();
            }
            if (chunk.interfaceNames() != null) {
                interfaceNames.addAll(chunk.interfaceNames());
            }
            if (chunk.methodName() != null && !chunk.methodName().isEmpty()) {
                methodNames.add(chunk.methodName());
            }
            if (chunk.referencedTypeNames() != null) {
                referencedTypes.addAll(chunk.referencedTypeNames());
            }
            if (chunk.calledMethodNames() != null) {
                calledMethods.addAll(chunk.calledMethodNames());
            }
            if (chunk.calledMethodRefs() != null) {           // [추가]
                calledMethodRefs.addAll(chunk.calledMethodRefs());
            }
        }

        return new StructuralInfo(className, superClassName,
            List.copyOf(interfaceNames), List.copyOf(methodNames),
            List.copyOf(referencedTypes), List.copyOf(calledMethods),
            List.copyOf(calledMethodRefs));                    // [추가]
    }

    private record StructuralInfo(
        String className,
        String superClassName,
        List<String> interfaceNames,
        List<String> methodNames,
        List<String> referencedTypeNames,
        List<String> calledMethodNames,
        List<CalledMethodRef> calledMethodRefs   // [추가]
    ) {
        static final StructuralInfo EMPTY = new StructuralInfo(
            null, null, List.of(), List.of(), List.of(), List.of(), List.of());

        boolean isEmpty() {
            return className == null && superClassName == null
                && interfaceNames.isEmpty() && methodNames.isEmpty()
                && referencedTypeNames.isEmpty() && calledMethodNames.isEmpty();
        }
    }

    // ── getter/setter 등 노이즈 메서드 판별 [추가] ──

    private boolean isMeaningfulMethodName(String name) {
        if (name == null || name.isBlank()) return false;
        if (GETTER_PATTERN.matcher(name).matches()) return false;
        if (SETTER_PATTERN.matcher(name).matches()) return false;
        return !COMMON_OBJECT_METHODS.contains(name);
    }

    // ── addFilteredResults ──

    private void addFilteredResults(
        List<ScoredVectorWithUnsignedIndices> results,
        Set<String> seenIds, Map<String, String> tags,
        float[] vector, String namespace,
        String filterField, String filterValue, String relationshipTag) {

        if (filterValue == null || filterValue.isEmpty()) return;

        Struct filter = Struct.newBuilder()
            .putFields(filterField, eqValue(filterValue))
            .build();

        List<ScoredVectorWithUnsignedIndices> filtered =
            pineconeClient.queryWithFilter(vector, METADATA_CANDIDATE_SIZE, namespace, filter);

        for (ScoredVectorWithUnsignedIndices r : filtered) {
            String id = r.getId();
            if (seenIds.add(id)) {
                results.add(r);
                tags.put(id, relationshipTag);
            }
        }
    }

    private void addFilteredResults(
        List<ScoredVectorWithUnsignedIndices> results,
        Set<String> seenIds, Map<String, String> tags,
        float[] vector, String namespace,
        String filterField, List<String> filterValues, String relationshipTag) {

        if (filterValues == null || filterValues.isEmpty()) return;

        // [변경] length()>4 기준 → isMeaningfulMethodName으로 통일
        List<String> meaningful = filterValues.stream()
            .filter(this::isMeaningfulMethodName)
            .toList();

        for (String value : meaningful.stream().limit(3).toList()) {
            addFilteredResults(results, seenIds, tags, vector, namespace,
                filterField, value, relationshipTag);
        }
    }

    // ── className + methodName AND 필터 [추가] ──
    // targetClassHint가 있는 호출을 동명이인 없이 정밀하게 찾기 위한 compound 필터.
    private void addFilteredResultsCompound(
        List<ScoredVectorWithUnsignedIndices> results,
        Set<String> seenIds, Map<String, String> tags,
        float[] vector, String namespace,
        String field1, String value1,
        String field2, String value2,
        String relationshipTag) {

        if (value1 == null || value1.isEmpty() || value2 == null || value2.isEmpty()) return;

        Struct filter = Struct.newBuilder()
            .putFields(field1, eqValue(value1))
            .putFields(field2, eqValue(value2))
            .build();

        List<ScoredVectorWithUnsignedIndices> filtered =
            pineconeClient.queryWithFilter(vector, METADATA_CANDIDATE_SIZE, namespace, filter);

        for (ScoredVectorWithUnsignedIndices r : filtered) {
            String id = r.getId();
            if (seenIds.add(id)) {
                results.add(r);
                tags.put(id, relationshipTag);
            }
        }
    }

    private com.google.protobuf.Value eqValue(String value) {
        return com.google.protobuf.Value.newBuilder()
            .setStructValue(Struct.newBuilder()
                .putFields("$eq", com.google.protobuf.Value.newBuilder().setStringValue(value).build())
                .build())
            .build();
    }

    // ── 유틸 ──

    private List<ScoredVectorWithUnsignedIndices> mergeCandidates(
        List<ScoredVectorWithUnsignedIndices> vectorCandidates,
        List<ScoredVectorWithUnsignedIndices> metadataCandidates,
        Set<String> changedFilesInPr) {

        Set<String> seenIds = new HashSet<>();
        List<ScoredVectorWithUnsignedIndices> merged = new ArrayList<>();

        Stream.concat(vectorCandidates.stream(), metadataCandidates.stream())
            .filter(r -> !changedFilesInPr.contains(getStringField(r, "filePath")))
            .forEach(r -> {
                if (seenIds.add(r.getId())) {
                    merged.add(r);
                }
            });

        return merged;
    }

    private String formatContext(List<ScoredVectorWithUnsignedIndices> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("[관련 코드 컨텍스트]\n");

        for (ScoredVectorWithUnsignedIndices result : results) {
            String filePath = getStringField(result, "filePath");
            String className = getStringField(result, "className");
            String methodSignature = getStringField(result, "methodSignature");
            String code = getStringField(result, "code");

            if (code.isBlank()) continue;

            sb.append("// ").append(filePath)
                .append(" (class: ").append(className)
                .append(", method: ").append(methodSignature)
                .append(")\n");
            sb.append(code).append("\n\n");
        }

        return sb.toString().trim();
    }

    private String truncate(String text, int limit) {
        return text.length() > limit ? text.substring(0, limit) + "\n...(truncated)" : text;
    }

    private String getStringField(ScoredVectorWithUnsignedIndices result, String key) {
        return result.getMetadata().getFieldsOrDefault(
            key, com.google.protobuf.Value.newBuilder().setStringValue("").build()
        ).getStringValue();
    }

    private List<String> getListField(ScoredVectorWithUnsignedIndices result, String key) {
        com.google.protobuf.Value value = result.getMetadata().getFieldsOrDefault(
            key, com.google.protobuf.Value.newBuilder().setStringValue("").build()
        );
        if (!value.hasListValue()) return List.of();
        return value.getListValue().getValuesList().stream()
            .map(com.google.protobuf.Value::getStringValue)
            .filter(s -> !s.isEmpty())
            .toList();
    }

    // ── Evaluation ──

    public List<ScoredVectorWithUnsignedIndices> searchForEvaluation(
        Long repoId, String filePath, String diff, String fileContent,
        int topK, Set<String> changedFilesInPr) {

        String embeddingInput = "File: " + filePath + "\nDiff:\n" + truncate(diff, 2000);
        float[] vector = embeddingService.embed(embeddingInput);
        String namespace = String.valueOf(repoId);

        StructuralInfo info = extractStructuralInfo(fileContent, filePath);

        List<ScoredVectorWithUnsignedIndices> vectorCandidates =
            pineconeClient.query(vector, VECTOR_CANDIDATE_SIZE, namespace);

        StructuralSearchResult structuralResult =
            searchByStructure(vector, namespace, filePath, info);

        List<ScoredVectorWithUnsignedIndices> allCandidates =
            mergeCandidates(vectorCandidates, structuralResult.candidates(), changedFilesInPr);

        List<ScoredVectorWithUnsignedIndices> structuralTop =
            selectTopStructural(structuralResult, changedFilesInPr,
                Math.min(MAX_STRUCTURAL_SLOTS, topK));

        Set<String> structuralIds = structuralTop.stream()
            .map(ScoredVectorWithUnsignedIndices::getId)
            .collect(Collectors.toSet());

        List<ScoredVectorWithUnsignedIndices> vectorOnly = allCandidates.stream()
            .filter(r -> !structuralIds.contains(r.getId()))
            .toList();

        String rerankQuery = buildRerankQuery(filePath, diff, info);

        int rerankTopN = Math.min(vectorOnly.size(), Math.max(topK, MAX_TOTAL_RESULTS) - structuralTop.size());
        List<ScoredVectorWithUnsignedIndices> rerankedVector =
            rerankCandidates(rerankQuery, vectorOnly, structuralResult.relationshipTags(), rerankTopN);

        List<ScoredVectorWithUnsignedIndices> combinedRanked = new ArrayList<>(structuralTop);
        rerankedVector.stream()
            .filter(r -> !structuralIds.contains(r.getId()))
            .forEach(combinedRanked::add);

        int dynamicLimit = computeDynamicLimit(combinedRanked, topK, Math.max(topK, MAX_TOTAL_RESULTS));
        return combinedRanked.stream()
            .limit(dynamicLimit)
            .toList();
    }

    /**
     * 상위 후보들 중 같은 className이 여러 번 나오면, 그 중복 개수만큼 결과 총량을 늘려서
     * 다른 관련 클래스가 밀려나지 않도록 한다. base(TOP_K)에서 시작해 max(MAX_TOTAL_RESULTS)까지 확장.
     */
    private int computeDynamicLimit(List<ScoredVectorWithUnsignedIndices> ranked, int base, int max) {
        int limit = Math.min(base, ranked.size());

        while (limit < max && limit < ranked.size()) {
            List<ScoredVectorWithUnsignedIndices> window = ranked.subList(0, limit);

            Map<String, Long> countByClass = window.stream()
                .collect(Collectors.groupingBy(
                    r -> getStringField(r, "className"), Collectors.counting()));

            long duplicateExtra = countByClass.values().stream()
                .filter(c -> c > 1)
                .mapToLong(c -> c - 1)
                .sum();

            int desired = Math.min(base + (int) duplicateExtra, max);
            if (desired <= limit) break;
            limit = Math.min(desired, ranked.size());
        }

        return limit;
    }
}