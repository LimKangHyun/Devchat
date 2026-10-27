package project.ai.processor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import project.common.dto.InlineReview;
import project.common.message.aireview.AiReviewRequestMessage;
import project.ai.budget.BudgetLevel;
import project.ai.budget.LlmBudgetExceededException;
import project.ai.budget.LlmBudgetService;
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
    private final RelatedChangedCodeBuilder relatedChangedCodeBuilder;
    private final LlmBudgetService llmBudgetService;

    public List<InlineReview> process(AiReviewRequestMessage message) {
        // 방의 오늘 LLM 사용량: 한도를 넘었으면 건너뛰고, 가까우면 맥락을 빼서 프롬프트를 줄인다
        BudgetLevel budget = llmBudgetService.check(message.chatRoomId());
        if (budget == BudgetLevel.EXCEEDED) {
            llmBudgetService.recordAction(LlmBudgetService.PURPOSE_REVIEW, budget);
            throw new LlmBudgetExceededException(message.chatRoomId());
        }

        Set<Integer> validDiffLines = diffLineParser.parseValidLines(message.fileDiff());

        String ragContext = "";
        String relatedChangedCode = "";
        if (budget == BudgetLevel.LOW) {
            llmBudgetService.recordAction(LlmBudgetService.PURPOSE_REVIEW, budget);
            log.info("[리뷰] LLM 사용량 한도에 가까워 맥락 없이 리뷰 - roomId={}, file={}",
                message.chatRoomId(), message.filePath());
        } else {
            ragContext = ragContextService.buildContext(
                message.repoId(), message.filePath(), message.fileDiff(),
                message.fileContent(), message.baseContent(),
                Set.copyOf(message.changedFilesInPr()));

            // 같은 PR에서 바뀐 다른 파일의 메서드 중 이 파일이 호출하는 것 (인덱스엔 변경 전 코드만 있어 RAG로는 못 가져온다)
            relatedChangedCode = relatedChangedCodeBuilder.build(
                message.filePath(), message.fileContent(), message.relatedChangedFiles());
        }

        List<InlineReview> reviews = geminiClient.reviewPrDiffInline(
                message.chatRoomId(), message.fileDiff(), ragContext, relatedChangedCode,
                message.fileContent(), message.prTitle(), message.prBody(), message.reviewConventions());

        List<InlineReview> filtered = inlineReviewFilter.filterValidReviews(reviews, message.fileContent());

        List<InlineReview> result = filtered.stream()
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

        // 튜닝용: LLM이 낸 코멘트가 어느 단계에서 얼마나 걸러지는지. 필터에서 많이 빠지면 프롬프트를 손볼 신호다.
        long teamRuleComments = result.stream()
                .filter(r -> r.comment() != null && r.comment().startsWith("[팀 규칙]")).count();
        log.info("[리뷰-결과] aiReviewId={}, file={}, 코멘트 생성={} → 필터 통과={}, 팀규칙 코멘트={}",
                message.aiReviewId(), message.filePath(), reviews.size(), result.size(), teamRuleComments);
        return result;
    }
}