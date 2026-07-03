package project.ai.processor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import project.common.dto.InlineReview;
import project.common.message.aireview.AiReviewRequestMessage;
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

    /**
     * 파일 하나에 대한 AI 코드 리뷰를 수행한다.
     * 1. 파일 diff에서 GitHub에 코멘트 가능한 변경 라인 번호 추출
     * 2. Pinecone RAG로 관련 컨텍스트 조회 (없으면 diff만 전달)
     * 3. Gemini에 리뷰 요청
     * 4. 유효하지 않은 리뷰(범위 초과, 빈 코멘트) 필터링
     * 5. GitHub PR API 제약상 변경 라인에만 코멘트 가능하므로 diffLine 매핑
     *
     * 예외는 여기서 삼키지 않고 그대로 상위(AiReviewRequestConsumer)로 전파한다.
     * 상위에서 catch하여 result stream에 FAIL로 발행해야 completedFiles/failedFiles
     * 카운팅과 AiReview 상태 판정이 정상 동작한다.
     */
    public List<InlineReview> process(AiReviewRequestMessage message) {
        Set<Integer> validDiffLines = diffParser.parseValidLines(message.fileDiff());

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
    }
}