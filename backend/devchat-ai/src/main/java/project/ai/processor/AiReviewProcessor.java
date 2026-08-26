package project.ai.processor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import project.common.dto.InlineReview;
import project.common.message.aireview.AiReviewRequestMessage;
import project.ai.client.GeminiClient;
import project.ai.rag.RagContextService;

import java.util.List;
import java.util.OptionalInt;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiReviewProcessor {

    private final GeminiClient geminiClient;
    private final RagContextService ragContextService;
    private final DiffLineParser diffLineParser;
    private final InlineReviewFilter inlineReviewFilter;

    public List<InlineReview> process(AiReviewRequestMessage message) {
        Set<Integer> validDiffLines = diffLineParser.parseValidLines(message.fileDiff());

        String ragContext = ragContextService.buildContext(
                message.repoId(), message.filePath(), message.fileDiff(),
                message.fileContent(), Set.copyOf(message.changedFilesInPr()));

        List<InlineReview> reviews = geminiClient.reviewPrDiffInline(
                message.fileDiff(), ragContext, message.fileContent(), message.prTitle(), message.prBody());

        List<InlineReview> filtered = inlineReviewFilter.filterValidReviews(reviews, message.fileContent());

        return filtered.stream()
                .map(r -> {
                    OptionalInt nearestLine = diffLineParser.findNearestDiffLine(validDiffLines, r.lineNumber());
                    if (nearestLine.isPresent()) {
                        return new InlineReview(r.lineNumber(), nearestLine.getAsInt(), r.comment());
                    } else {
                        log.info("[리뷰] 유효 라인 매핑 실패, 전체 코멘트로 전환. lineNumber={}", r.lineNumber());
                        return r.asGeneralComment();
                    }
                })
                .toList();
    }
}