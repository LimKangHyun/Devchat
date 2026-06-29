package project.ai.processor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import project.common.dto.InlineReview;
import project.common.message.AiReviewRequestMessage;
import project.ai.client.GeminiClient;
import project.ai.domain.aireview.app.AiReviewDiffParser;
import project.ai.service.RagContextService;

import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiReviewProcessor {

    private final GeminiClient geminiClient;
    private final RagContextService ragContextService;
    private final AiReviewDiffParser diffParser;

    public List<InlineReview> process(AiReviewRequestMessage message) {
        try {
            Set<Integer> validDiffLines = diffParser.parseDiffLines(message.fileDiff())
                    .getOrDefault(message.filePath(), Set.of());

            String ragContext = ragContextService.buildContext(
                    message.repoId(), message.filePath(), message.fileDiff());

            String diffWithContext = ragContext.isBlank()
                    ? message.fileDiff()
                    : ragContext + "\n\n위 컨텍스트를 참고해서 아래 PR을 리뷰해줘:\n\n" + message.fileDiff();

            List<InlineReview> reviews = geminiClient.reviewPrDiffInline(
                    diffWithContext, message.fileContent(), message.prTitle(), message.prBody());

            List<InlineReview> filtered = diffParser.filterValidReviews(reviews, message.fileContent());

            return filtered.stream()
                    .map(r -> {
                        int diffLine = validDiffLines.contains(r.lineNumber())
                                ? r.lineNumber()
                                : diffParser.findNearestDiffLine(validDiffLines, r.lineNumber());
                        return new InlineReview(r.lineNumber(), diffLine, r.comment());
                    })
                    .toList();

        } catch (Exception e) {
            log.error("파일 리뷰 실패: filePath={}", message.filePath(), e);
            return List.of();
        }
    }
}