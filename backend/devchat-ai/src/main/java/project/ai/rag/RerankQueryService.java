package project.ai.rag;

import io.pinecone.unsigned_indices_model.ScoredVectorWithUnsignedIndices;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import project.ai.client.CohereClient;
import project.ai.indexing.structural.MethodNameFilter;
import project.ai.indexing.structural.ProjectTypeFilter;
import project.ai.indexing.structural.StructuralInfo;

import java.util.List;
import java.util.Map;

import static project.ai.rag.PineconeResultUtils.getListField;
import static project.ai.rag.PineconeResultUtils.getStringField;

/** 리랭킹용 쿼리/문서 조립과 Cohere 호출을 담당한다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class RerankQueryService {

    private final CohereClient cohereClient;

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

    public String buildRerankQuery(String filePath, String diff, StructuralInfo info) {
        StringBuilder sb = new StringBuilder();
        sb.append("변경 파일: ").append(filePath).append("\n");

        if (info.className() != null) sb.append("변경 클래스: ").append(info.className()).append("\n");
        if (info.superClassName() != null) sb.append("상속: ").append(info.superClassName()).append("\n");
        if (!info.interfaceNames().isEmpty()) {
            sb.append("구현: ").append(String.join(", ", info.interfaceNames())).append("\n");
        }

        List<String> projectTypes = info.referencedTypeNames().stream()
                .filter(t -> ProjectTypeFilter.isProjectType(t, info.imports()))
                .limit(10).toList();
        if (!projectTypes.isEmpty()) {
            sb.append("사용하는 타입: ").append(String.join(", ", projectTypes)).append("\n");
        }

        List<String> meaningful = info.calledMethodNames().stream()
                .filter(MethodNameFilter::isMeaningful).limit(10).toList();
        if (!meaningful.isEmpty()) {
            sb.append("호출하는 메서드: ").append(String.join(", ", meaningful)).append("\n");
        }

        sb.append("이 변경이 영향을 줄 수 있는 호출 관계, 상속 관계, 타입 참조 코드를 찾아야 함.\n");
        sb.append("Diff:\n").append(truncate(diff, 1000));
        return sb.toString();
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