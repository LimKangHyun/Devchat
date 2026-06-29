package project.ai.service;

import io.pinecone.unsigned_indices_model.ScoredVectorWithUnsignedIndices;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import project.ai.client.PineconeClient;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagContextService {

    private final EmbeddingService embeddingService;
    private final PineconeClient pineconeClient;

    private static final int TOP_K = 5;
    private static final float PATH_BOOST = 0.2f;

    /**
     * PR diff로 관련 코드 청크 검색 후 컨텍스트 문자열 반환a
     * 실패 시 빈 문자열 반환 (리뷰 흐름 중단 없음)
     */
    public String buildContext(Long repoId, String filePath, String diff) {
        try {
            // 파일 경로 + diff 조합으로 임베딩 생성
            String input = "File: " + filePath + "\nDiff:\n" + truncate(diff, 2000);
            float[] vector = embeddingService.embed(input);

            List<ScoredVectorWithUnsignedIndices> results = pineconeClient.query(vector, TOP_K, String.valueOf(repoId));
            if (results.isEmpty()) return "";

            results.forEach(r -> {
                String path = r.getMetadata().getFieldsOrDefault(
                        "filePath", com.google.protobuf.Value.newBuilder().setStringValue("").build()
                ).getStringValue();
                log.info("RAG 검색 결과 - filePath={}, score={}", path, r.getScore());
            });

            // repoId 필터링 + 파일 경로 boost 적용 후 정렬
            String fileNameOnly = extractFileName(filePath);

            List<ScoredVectorWithUnsignedIndices> filtered = results.stream()
                    .sorted((a, b) -> {
                        float scoreA = boostedScore(a, fileNameOnly);
                        float scoreB = boostedScore(b, fileNameOnly);
                        return Float.compare(scoreB, scoreA);
                    })
                    .toList();

            if (filtered.isEmpty()) return "";

            return formatContext(filtered);

        } catch (Exception e) {
            log.warn("RAG 컨텍스트 조회 실패, 빈 문자열 반환. repoId={}, filePath={}", repoId, filePath, e);
            return "";
        }
    }

    /**
     * 파일명 일치 시 score에 boost 부여
     */
    private float boostedScore(ScoredVectorWithUnsignedIndices result, String fileNameOnly) {
        float score = result.getScore();
        String resultPath = result.getMetadata().getFieldsOrDefault(
                "filePath", com.google.protobuf.Value.newBuilder().setStringValue("").build()
        ).getStringValue();

        if (resultPath.contains(fileNameOnly)) {
            score += PATH_BOOST;
        }
        return score;
    }

    /**
     * 검색된 청크들을 프롬프트에 주입할 문자열로 포맷
     */
    private String formatContext(List<ScoredVectorWithUnsignedIndices> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("[관련 코드 컨텍스트]\n");

        for (ScoredVectorWithUnsignedIndices result : results) {
            String filePath = result.getMetadata().getFieldsOrDefault(
                    "filePath", com.google.protobuf.Value.newBuilder().setStringValue("").build()
            ).getStringValue();
            String code = result.getMetadata().getFieldsOrDefault(
                    "code", com.google.protobuf.Value.newBuilder().setStringValue("").build()
            ).getStringValue();

            if (code.isBlank()) continue;

            sb.append("// ").append(filePath).append("\n");
            sb.append(code).append("\n\n");
        }

        return sb.toString().trim();
    }

    private String extractFileName(String filePath) {
        int idx = filePath.lastIndexOf("/");
        return idx >= 0 ? filePath.substring(idx + 1) : filePath;
    }

    private String truncate(String text, int limit) {
        return text.length() > limit ? text.substring(0, limit) + "\n...(truncated)" : text;
    }
}