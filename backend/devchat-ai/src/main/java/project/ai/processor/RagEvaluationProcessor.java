package project.ai.processor;

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
            message.baseContent(),
            5,
            Set.copyOf(message.changedFilesInPr())
        );

        results.forEach(r -> {
            log.info("[RAG EVAL] score={}, path={}, class={}, method={}",
                r.score(),
                r.getString("filePath"),
                r.getString("className"),
                r.getString("methodSignature"));
        });

        return List.of();
    }
}