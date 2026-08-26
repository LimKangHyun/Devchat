package project.ai.rag;

import io.pinecone.unsigned_indices_model.ScoredVectorWithUnsignedIndices;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import project.ai.client.PineconeClient;
import project.ai.indexing.structural.ChangedSymbol;
import project.ai.indexing.structural.ChangedSymbolResolver;
import project.ai.indexing.structural.StructuralInfo;
import project.ai.indexing.structural.StructuralInfoExtractor;
import project.ai.indexing.EmbeddingService;
import project.ai.processor.DiffLineParser;
import project.common.exception.ex.IndexingException;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static project.ai.rag.PineconeResultUtils.getStringField;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagContextService {

    private final EmbeddingService embeddingService;
    private final PineconeClient pineconeClient;
    private final StructuralInfoExtractor structuralInfoExtractor;
    private final StructuralSearchService structuralSearchService;
    private final RerankQueryService rerankQueryService;
    private final DiffLineParser diffLineParser;
    private final ChangedSymbolResolver changedSymbolResolver;

    private static final int TOP_K = 5;
    private static final int MAX_TOTAL_RESULTS = 9;
    private static final int MAX_STRUCTURAL_SLOTS = 3;
    private static final int VECTOR_CANDIDATE_SIZE = 30;

    public String buildContext(Long repoId, String filePath, String diff,
                               String fileContent, Set<String> changedFilesInPr) {
        try {
            List<ScoredVectorWithUnsignedIndices> results = search(
                    repoId, filePath, diff, fileContent, changedFilesInPr, TOP_K, MAX_TOTAL_RESULTS);

            if (results.isEmpty()) {
                log.info("[RAG] repoId={}, filePath={}, 검색결과 없음", repoId, filePath);
                return "";
            }

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

    public List<ScoredVectorWithUnsignedIndices> searchForEvaluation(
            Long repoId, String filePath, String diff, String fileContent,
            int topK, Set<String> changedFilesInPr) {

        return search(repoId, filePath, diff, fileContent, changedFilesInPr,
                topK, Math.max(topK, MAX_TOTAL_RESULTS));
    }

    private List<ScoredVectorWithUnsignedIndices> search(
            Long repoId, String filePath, String diff, String fileContent,
            Set<String> changedFilesInPr, int topK, int maxTotal) {

        String embeddingInput = "File: " + filePath + "\nDiff:\n" + truncate(diff, 2000);
        float[] vector = embeddingService.embedQuery(embeddingInput);
        String namespace = String.valueOf(repoId);

        StructuralInfo info = structuralInfoExtractor.extract(fileContent, filePath);

        Set<Integer> changedLines = diffLineParser.parseValidLines(diff);
        List<ChangedSymbol> changedSymbols = changedSymbolResolver.resolve(fileContent, changedLines);

        List<ScoredVectorWithUnsignedIndices> vectorCandidates =
                pineconeClient.query(vector, VECTOR_CANDIDATE_SIZE, namespace);

        StructuralSearchService.StructuralSearchResult structural =
                structuralSearchService.search(vector, namespace, filePath, info, changedSymbols);

        List<ScoredVectorWithUnsignedIndices> allCandidates =
                mergeCandidates(vectorCandidates, structural.candidates(), changedFilesInPr);

        if (allCandidates.isEmpty()) return List.of();

        log.info("[RAG] 후보 수: vector={}, metadata={}, 합산(중복제거)={}",
                vectorCandidates.size(), structural.candidates().size(), allCandidates.size());

        List<ScoredVectorWithUnsignedIndices> structuralTop =
                structuralSearchService.selectTopStructural(
                        structural, changedFilesInPr, Math.min(MAX_STRUCTURAL_SLOTS, topK));

        Set<String> structuralIds = structuralTop.stream()
                .map(ScoredVectorWithUnsignedIndices::getId)
                .collect(Collectors.toSet());

        List<ScoredVectorWithUnsignedIndices> vectorOnly = allCandidates.stream()
                .filter(r -> !structuralIds.contains(r.getId()))
                .toList();

        int rerankTopN = Math.min(vectorOnly.size(), maxTotal - structuralTop.size());
        String rerankQuery = rerankQueryService.buildRerankQuery(filePath, diff, info);
        List<ScoredVectorWithUnsignedIndices> reranked = rerankQueryService.rerank(
                rerankQuery, vectorOnly, structural.relationshipTags(), rerankTopN);

        List<ScoredVectorWithUnsignedIndices> combined = new ArrayList<>(structuralTop);
        reranked.stream()
                .filter(r -> !structuralIds.contains(r.getId()))
                .forEach(combined::add);

        int dynamicLimit = computeDynamicLimit(combined, topK, maxTotal);
        return combined.stream().limit(dynamicLimit).toList();
    }

    private List<ScoredVectorWithUnsignedIndices> mergeCandidates(
            List<ScoredVectorWithUnsignedIndices> vectorCandidates,
            List<ScoredVectorWithUnsignedIndices> metadataCandidates,
            Set<String> changedFilesInPr) {

        Set<String> seenIds = new HashSet<>();
        List<ScoredVectorWithUnsignedIndices> merged = new ArrayList<>();

        Stream.concat(vectorCandidates.stream(), metadataCandidates.stream())
                .filter(r -> !changedFilesInPr.contains(getStringField(r, "filePath")))
                .forEach(r -> {
                    if (seenIds.add(r.getId())) merged.add(r);
                });

        return merged;
    }

    /**
     * 상위 후보들 중 같은 className이 여러 번 나오면, 그 중복 개수만큼 결과 총량을 늘려서
     * 다른 관련 클래스가 밀려나지 않도록 한다. base(topK)에서 시작해 max까지 확장.
     */
    private int computeDynamicLimit(List<ScoredVectorWithUnsignedIndices> ranked, int base, int max) {
        int limit = Math.min(base, ranked.size());

        while (limit < max && limit < ranked.size()) {
            List<ScoredVectorWithUnsignedIndices> window = ranked.subList(0, limit);

            Map<String, Long> countByClass = window.stream()
                    .collect(Collectors.groupingBy(
                            r -> getStringField(r, "className"), Collectors.counting()));

            long duplicateExtra = countByClass.values().stream()
                    .filter(c -> c > 1).mapToLong(c -> c - 1).sum();

            int desired = Math.min(base + (int) duplicateExtra, max);
            if (desired <= limit) break;
            limit = Math.min(desired, ranked.size());
        }

        return limit;
    }

    private String formatContext(List<ScoredVectorWithUnsignedIndices> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("[관련 코드 컨텍스트]\n");

        for (ScoredVectorWithUnsignedIndices result : results) {
            String code = getStringField(result, "code");
            if (code.isBlank()) continue;

            sb.append("// ").append(getStringField(result, "filePath"))
                    .append(" (class: ").append(getStringField(result, "className"))
                    .append(", method: ").append(getStringField(result, "methodSignature"))
                    .append(")\n");
            sb.append(code).append("\n\n");
        }

        return sb.toString().trim();
    }

    private String truncate(String text, int limit) {
        return text.length() > limit ? text.substring(0, limit) + "\n...(truncated)" : text;
    }
}