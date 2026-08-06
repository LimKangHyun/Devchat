package project.ai.service;

import io.pinecone.unsigned_indices_model.ScoredVectorWithUnsignedIndices;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import project.ai.client.PineconeClient;
import project.common.exception.ex.IndexingException;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagContextService {

    private final EmbeddingService embeddingService;
    private final PineconeClient pineconeClient;
    private final CohereRerankService cohereRerankService;

    private static final int TOP_K = 5;
    private static final int CANDIDATE_SIZE = 30;

    public String buildContext(Long repoId, String filePath, String diff, Set<String> changedFilesInPr) {
        try {
            String input = "File: " + filePath + "\nDiff:\n" + truncate(diff, 2000);
            float[] vector = embeddingService.embed(input);

            // 1. Pinecone에서 후보 넉넉하게 가져오기
            List<ScoredVectorWithUnsignedIndices> candidates =
                pineconeClient.query(vector, CANDIDATE_SIZE, String.valueOf(repoId));

            // 2. 변경된 파일 제외
            candidates = candidates.stream()
                .filter(r -> !changedFilesInPr.contains(getStringField(r, "filePath")))
                .toList();

            if (candidates.isEmpty()) {
                log.info("[RAG] repoId={}, filePath={}, 검색결과 없음", repoId, filePath);
                return "";
            }

            // 3. Cohere Rerank
            List<ScoredVectorWithUnsignedIndices> reranked = rerankCandidates(input, candidates);

            // 4. 상위 TOP_K개 선택
            List<ScoredVectorWithUnsignedIndices> results = reranked.stream()
                .limit(TOP_K)
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

    /**
     * Cohere Rerank를 적용한다.
     * 실패 시 원본 순서(vector score 순)를 그대로 반환하여 리뷰 흐름이 중단되지 않도록 한다.
     */
    private List<ScoredVectorWithUnsignedIndices> rerankCandidates(
        String query, List<ScoredVectorWithUnsignedIndices> candidates) {

        List<String> documents = candidates.stream()
            .map(this::buildRerankDocument)
            .toList();

        List<CohereRerankService.RerankResult> rerankResults =
            cohereRerankService.rerank(query, documents, TOP_K);

        // rerank 실패 시 원본 순서 유지
        if (rerankResults.isEmpty()) return candidates;

        return rerankResults.stream()
            .map(r -> candidates.get(r.index()))
            .toList();
    }

    /**
     * Pinecone 검색 결과 하나를 Cohere Rerank에 보낼 document 텍스트로 조립한다.
     * code 원문에 없는 메타데이터(상속, 구현, 호출 관계)를 앞에 붙여서
     * rerank가 코드 간 연관성을 판단할 수 있게 한다.
     */
    private String buildRerankDocument(ScoredVectorWithUnsignedIndices result) {
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

    public List<ScoredVectorWithUnsignedIndices> searchForEvaluation(
        Long repoId, String filePath, String diff, int topK, Set<String> changedFilesInPr) {

        String input = "File: " + filePath + "\nDiff:\n" + truncate(diff, 2000);
        float[] vector = embeddingService.embed(input);

        List<ScoredVectorWithUnsignedIndices> candidates =
            pineconeClient.query(vector, CANDIDATE_SIZE, String.valueOf(repoId))
                .stream()
                .filter(r -> !changedFilesInPr.contains(getStringField(r, "filePath")))
                .toList();

        List<ScoredVectorWithUnsignedIndices> reranked = rerankCandidates(input, candidates);
        return reranked.stream().limit(topK).toList();
    }
}