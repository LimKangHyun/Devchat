package project.ai.processor;

import io.pinecone.unsigned_indices_model.ScoredVectorWithUnsignedIndices;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import project.ai.rag.RagContextService;
import project.common.dto.InlineReview;
import project.common.message.aireview.AiReviewRequestMessage;

@Slf4j
@Component
@RequiredArgsConstructor
public class RagEvaluationProcessor {

    private final RagContextService ragContextService;

    public List<InlineReview> process(AiReviewRequestMessage message) {

        var results = ragContextService.searchForEvaluation(
                message.repoId(),
                message.filePath(),
                message.fileDiff(),
                message.fileContent(),
                5,
                Set.copyOf(message.changedFilesInPr())
        );

        results.forEach(r -> {
            log.info("[RAG EVAL] score={}, path={}, class={}, method={}",
                    r.getScore(),
                    getMetaField(r, "filePath"),
                    getMetaField(r, "className"),
                    getMetaField(r, "methodSignature"));
        });

        return List.of();
    }

    private String getMetaField(
            ScoredVectorWithUnsignedIndices result,
            String key) {

        return result.getMetadata()
                .getFieldsOrDefault(
                        key,
                        com.google.protobuf.Value.newBuilder()
                                .setStringValue("")
                                .build()
                )
                .getStringValue();
    }
}