package project.ai.rag;

import io.pinecone.unsigned_indices_model.ScoredVectorWithUnsignedIndices;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import project.ai.client.CohereClient;
import project.ai.indexing.chunking.ChunkMeta.CalledMethodRef;
import project.ai.indexing.structural.ChangedSymbol;
import project.ai.indexing.structural.MethodNameFilter;
import project.ai.indexing.structural.ProjectTypeFilter;
import project.ai.indexing.structural.StructuralInfo;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static project.ai.rag.PineconeResultUtils.getListField;
import static project.ai.rag.PineconeResultUtils.getStringField;

/** 리랭킹용 쿼리/문서 조립과 Cohere 호출을 담당한다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class RerankQueryService {

    private final CohereClient cohereClient;

    private static final int MAX_SYMBOLS = 10;

    public List<ScoredVectorWithUnsignedIndices> rerank(
            String query, List<ScoredVectorWithUnsignedIndices> candidates,
            Map<String, String> relationshipTags, int topN) {

        if (candidates.isEmpty() || topN <= 0) return List.of();

        List<String> documents = candidates.stream()
                .map(r -> buildRerankDocument(r, relationshipTags))
                .toList();

        List<CohereClient.RerankResult> rerankResults =
                cohereClient.rerank(query, documents, topN);

        if (rerankResults.isEmpty()) {
            // 리랭킹 실패. 폴백 결과는 벡터 유사도 순 상위 topN이며 리랭킹 결과가 아니다.
            // 개수를 topN으로 맞추지 않으면 후속 단계가 의도보다 많은 후보를 받고,
            // 평가 중 이 경로를 타면 "리랭킹 없는 파이프라인"을 측정한 것이 되므로
            // 반드시 로그로 드러나야 한다.
            log.warn("[RAG] 리랭킹 실패, 벡터 순서 상위 {}개로 폴백. pool={}", topN, candidates.size());
            return candidates.stream().limit(topN).toList();
        }

        return rerankResults.stream()
                .map(r -> candidates.get(r.index()))
                .toList();
    }

    /**
     * 리랭크 쿼리를 조립한다.
     *
     * 리랭커는 쿼리와 문서를 한 쌍으로 읽고 점수를 매기므로, 문서(buildRerankDocument)의
     * calls/references에 등장할 이름이 쿼리에도 있어야 연결 고리로 인식된다.
     * 호출자 문서는 calls에 "변경된 메서드 이름"을 담고 있으므로, 파일 전체 구조가 아니라
     * 변경/삭제된 심볼을 쿼리 앞쪽에 둔다. 라벨도 문서와 같은 영어 표기로 맞춘다.
     *
     * @param changedSymbols 비어 있으면(diff 매핑 실패 등) 파일 전체 구조 기준으로 폴백한다.
     * @param removedSymbols 변경 전에는 있었으나 변경 후 사라진 메서드 이름
     */
    public String buildRerankQuery(String filePath, String diff, StructuralInfo info,
                                   List<ChangedSymbol> changedSymbols, Set<String> removedSymbols) {
        StringBuilder sb = new StringBuilder();
        sb.append("file: ").append(filePath).append("\n");

        if (info.className() != null) {
            sb.append("changed_class: ").append(info.className());
            if (info.superClassName() != null) sb.append(" extends ").append(info.superClassName());
            if (!info.interfaceNames().isEmpty()) {
                sb.append(" implements ").append(String.join(", ", info.interfaceNames()));
            }
            sb.append("\n");
        }

        List<String> changedMethods = changedSymbols.stream()
                .filter(s -> s.kind() == ChangedSymbol.Kind.METHOD_SIGNATURE
                        || s.kind() == ChangedSymbol.Kind.METHOD_BODY)
                .map(s -> s.name() + (s.kind() == ChangedSymbol.Kind.METHOD_SIGNATURE
                        ? " (signature changed)" : " (body changed)"))
                .limit(MAX_SYMBOLS).toList();
        if (!changedMethods.isEmpty()) {
            sb.append("changed_methods: ").append(String.join(", ", changedMethods)).append("\n");
        }

        List<String> changedFields = changedSymbols.stream()
                .filter(s -> s.kind() == ChangedSymbol.Kind.FIELD)
                .map(ChangedSymbol::name)
                .limit(MAX_SYMBOLS).toList();
        if (!changedFields.isEmpty()) {
            sb.append("changed_fields: ").append(String.join(", ", changedFields)).append("\n");
        }

        if (changedSymbols.stream().anyMatch(s -> s.kind() == ChangedSymbol.Kind.CLASS_HEADER)) {
            sb.append("class_declaration_changed: true\n");
        }

        List<String> removed = removedSymbols.stream()
                .filter(MethodNameFilter::isMeaningful)
                .limit(MAX_SYMBOLS).toList();
        if (!removed.isEmpty()) {
            sb.append("removed_methods: ").append(String.join(", ", removed)).append("\n");
        }

        // 변경 코드가 "쓰는" 쪽(맥락용). 변경된 메서드가 확정되면 그 메서드가 호출하는 것만 남겨
        // 바뀌지 않은 메서드의 호출 대상이 쿼리를 희석하지 않게 한다.
        sb.append(formatList("calls: ", usedMethodNames(info, changedSymbols)));

        List<String> projectTypes = info.referencedTypeNames().stream()
                .filter(t -> ProjectTypeFilter.isProjectType(t, info.imports()))
                .limit(10).toList();
        sb.append(formatList("references: ", projectTypes));

        sb.append(buildIntent(info.className(), !changedMethods.isEmpty() || !removed.isEmpty()));
        sb.append("Diff:\n").append(truncate(diff, 1000));
        return sb.toString();
    }

    /** 변경된 메서드가 있으면 그 메서드의 호출 대상만, 없으면 파일 전체의 호출 대상. */
    private List<String> usedMethodNames(StructuralInfo info, List<ChangedSymbol> changedSymbols) {
        Set<String> changedMethodNames = changedSymbols.stream()
                .filter(s -> s.kind() == ChangedSymbol.Kind.METHOD_SIGNATURE
                        || s.kind() == ChangedSymbol.Kind.METHOD_BODY)
                .map(ChangedSymbol::name)
                .collect(Collectors.toSet());

        if (changedMethodNames.isEmpty()) {
            return info.calledMethodNames().stream()
                    .filter(MethodNameFilter::isMeaningful).limit(10).toList();
        }

        return info.calledMethodRefs().stream()
                .filter(ref -> changedMethodNames.contains(ref.fromMethodName()))
                .map(CalledMethodRef::methodName)
                .filter(MethodNameFilter::isMeaningful)
                .distinct().limit(10).toList();
    }

    /**
     * 검색 의도. "영향을 줄 수 있는 코드"처럼 모호하게 쓰지 않고,
     * 문서의 어떤 필드가 쿼리의 무엇과 일치해야 하는지를 적는다.
     */
    private String buildIntent(String className, boolean hasMethodChanges) {
        if (className == null) {
            return "Find code affected by this change.\n";
        }
        StringBuilder sb = new StringBuilder("Find code that ");
        if (hasMethodChanges) {
            sb.append("calls changed_methods or removed_methods of ").append(className).append(", ");
        }
        sb.append("implements or extends ").append(className)
                .append(", or references ").append(className).append(".\n");
        return sb.toString();
    }

    private String formatList(String label, List<String> values) {
        return values.isEmpty() ? "" : label + String.join(", ", values) + "\n";
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
        if (tag != null) sb.append("[관계: ").append(tag).append("]\n");

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

    private String truncate(String text, int limit) {
        return text.length() > limit ? text.substring(0, limit) + "\n...(truncated)" : text;
    }
}