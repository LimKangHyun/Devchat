package project.api.domain.aireview.outbox;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import project.api.domain.aireview.app.AiReviewDiffParser;
import project.api.domain.aireview.app.AiReviewService;
import project.api.domain.aireview.entity.AiReview;
import project.api.domain.aireview.entity.AiReviewStatus;
import project.api.domain.github.GitRepoUrlUtils;
import project.api.domain.github.client.GitHubBotClient;
import project.api.global.outbox.OutboxEvent;
import project.api.global.outbox.OutboxEventType;
import project.api.global.outbox.OutboxHandler;
import project.api.global.outbox.OutboxService;
import project.common.dto.github.GitRepoDto;
import project.common.message.aireview.AiReviewRequestMessage;
import project.common.message.aireview.ChangedFileSnapshot;

/**
 * AI 리뷰 준비. GitHub에서 리뷰할 파일과 변경 전·후 코드를 모아 파일별 리뷰 요청을 outbox에 적는다.
 *
 * 예전에는 웹훅 커밋 뒤 @Async 리스너가 이 일을 하고 Redis로 바로 보냈다. 그 사이 GitHub·Redis가 실패하거나
 * 서버가 죽으면 리뷰가 PENDING에 영원히 남았다. 이제 실패하면 OutboxRelay가 백오프 뒤 다시 시도하고,
 * 끝내 실패하면 리뷰를 FAIL로 끝낸다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiReviewPrepareHandler implements OutboxHandler {

    private static final int MAX_CHANGED_FILES = 20;
    private static final int MAX_TOTAL_DIFF_LINES = 2500;
    private static final int MAX_FILE_DIFF_LINES = 250;
    /** 리뷰 요청 하나에 같이 보내는 다른 변경 파일 수 상한. 메시지 크기를 제한한다. */
    private static final int MAX_RELATED_CHANGED_FILES = 5;

    private static final Set<String> REVIEWABLE_EXTENSIONS = Set.of(
            ".java", ".kt", ".py", ".js", ".ts", ".tsx", ".jsx"
    );

    private final AiReviewService aiReviewService;
    private final AiReviewDiffParser diffParser;
    private final GitHubBotClient gitHubBotClient;
    private final AiReviewOutbox aiReviewOutbox;
    private final OutboxService outboxService;
    private final MeterRegistry meterRegistry;

    @Override
    public OutboxEventType type() {
        return OutboxEventType.AI_REVIEW_PREPARE;
    }

    @Override
    public void handle(OutboxEvent event) {
        AiReviewPreparePayload payload = outboxService.readPayload(event, AiReviewPreparePayload.class);
        AiReview aiReview = aiReviewService.findByIdWithChatRoom(payload.aiReviewId());

        // 다시 처리돼도 요청이 두 번 만들어지지 않게: 그사이 새 커밋으로 리셋됐거나, 이미 끝났거나, 이미 준비했으면 건너뛴다
        if (aiReview.getStatus() != AiReviewStatus.PENDING
                || !payload.headSha().equals(aiReview.getCommitSha())
                || aiReview.getTotalFiles() > 0) {
            log.info("리뷰 준비 건너뜀 (이미 준비했거나 새 커밋으로 바뀜) - aiReviewId={}, headSha={}",
                    aiReview.getId(), payload.headSha());
            return;
        }

        GitRepoDto repo = GitRepoUrlUtils.validateAndParseUrl(aiReview.getChatRoom().getRepositoryUrl());

        Map<String, String> fileDiffs = diffParser.parseFileDiffs(aiReview.getPrDiff());
        Map<String, String> fileStatuses = gitHubBotClient.getPrFileStatuses(
                repo.ownerName(), repo.repoName(), aiReview.getPrNumber());

        // 실제 리뷰 대상 파일만 먼저 걸러냄 (삭제된 파일 제외, 리뷰 가능 확장자만)
        // GitHub API는 삭제된 파일의 status를 "removed"로 준다.
        Map<String, String> reviewableFileDiffs = fileDiffs.entrySet().stream()
                .filter(e -> !"removed".equals(fileStatuses.getOrDefault(e.getKey(), "modified")))
                .filter(e -> isReviewableFile(e.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));

        if (reviewableFileDiffs.size() > MAX_CHANGED_FILES) {
            aiReviewService.updateSkipped(aiReview, "변경 파일 수 초과 (" + reviewableFileDiffs.size() + "개)");
            return;
        }

        List<String> changedFilesInPr = List.copyOf(reviewableFileDiffs.keySet());

        long totalDiffLines = reviewableFileDiffs.values().stream()
                .mapToLong(diff -> diff.lines().count())
                .sum();
        if (totalDiffLines > MAX_TOTAL_DIFF_LINES) {
            aiReviewService.updateSkipped(aiReview, "총 diff 라인 수 초과 (" + totalDiffLines + "줄)");
            return;
        }

        // 1) 리뷰할 파일의 변경 전/후 코드를 먼저 모두 가져온다.
        //    파일 A를 리뷰할 때 같은 PR에서 바뀐 파일 B의 새 코드가 필요하므로, 요청을 만들기 전에 전부 모아야 한다.
        List<ReviewTarget> targets = new ArrayList<>();
        for (Map.Entry<String, String> entry : reviewableFileDiffs.entrySet()) {
            String filePath = entry.getKey();
            String fileDiff = entry.getValue();
            String status = fileStatuses.getOrDefault(filePath, "modified");

            if (fileDiff.lines().count() > MAX_FILE_DIFF_LINES) {
                log.info("파일 diff 라인 수 초과로 리뷰 스킵: {}", filePath);
                aiReviewService.addSkippedFile(aiReview.getId(), filePath,
                        "파일 변경 라인 수 초과 (" + fileDiff.lines().count() + "줄)");
                continue;
            }

            String fileContent = gitHubBotClient.getFileContent(
                    repo.ownerName(), repo.repoName(), filePath, payload.headSha());
            String baseContent = "added".equals(status) ? ""
                    : gitHubBotClient.getFileContent(
                    repo.ownerName(), repo.repoName(), filePath, payload.baseSha());

            targets.add(new ReviewTarget(filePath, fileDiff, fileContent, baseContent));
        }

        // 2) 증분 리뷰면 이전 결과를 재사용하는 파일은 요청하지 않는다.
        //    재사용 파일도 위에서 코드를 가져왔으므로, 다른 파일을 리뷰할 때 "같이 바뀐 파일" 문맥은 전체 리뷰와 같다.
        Set<String> reused = payload.reusedFiles();
        List<ReviewTarget> toReview = targets.stream().filter(t -> !reused.contains(t.filePath())).toList();
        if (!reused.isEmpty()) {
            log.info("[증분 리뷰] aiReviewId={}, 다시 리뷰 {}개, 재사용 {}개",
                    aiReview.getId(), toReview.size(), targets.size() - toReview.size());
        }

        if (toReview.isEmpty()) {
            recordFileCounts(reused, 0, targets.size());
            aiReviewService.completeWithoutNewFiles(aiReview.getId(), !reused.isEmpty());
            return;
        }

        // 3) 파일별 리뷰 요청을 만들어 총 파일 수·파일 코드와 함께 한 트랜잭션으로 outbox에 적는다.
        List<AiReviewRequestMessage> requests = new ArrayList<>();
        Map<String, String> fileContentMap = new HashMap<>();
        Long roomId = aiReview.getChatRoom().getId();
        for (ReviewTarget target : toReview) {
            fileContentMap.put(target.filePath(), target.fileContent());
            requests.add(new AiReviewRequestMessage(
                    aiReview.getId(), roomId, roomId,
                    target.filePath(), target.fileDiff(), target.fileContent(), target.baseContent(),
                    aiReview.getPrTitle(), aiReview.getPrBody(),
                    changedFilesInPr,
                    findRelatedChangedFiles(target, targets),
                    aiReview.getChatRoom().getReviewConventions(),
                    aiReview.getCommitSha()));
        }
        aiReviewOutbox.savePrepared(aiReview.getId(), requests, fileContentMap, reused);
        recordFileCounts(reused, toReview.size(), targets.size() - toReview.size());
    }

    @Override
    public void onGiveUp(OutboxEvent event, String error) {
        AiReviewPreparePayload payload = outboxService.readPayload(event, AiReviewPreparePayload.class);
        aiReviewService.failPendingReview(payload.aiReviewId(), "리뷰 준비 실패 (GitHub 응답 없음 등). 다시 시도해 주세요.");
    }

    /**
     * 리뷰 요청한 파일과 재사용한 파일 수. 재사용 파일 하나가 LLM 호출 하나를 아낀 것이다.
     * Grafana: sum(ai_review_files_total{result="reused"}) / sum(ai_review_files_total)
     */
    private void recordFileCounts(Set<String> reusedFiles, int reviewed, int reused) {
        String mode = reusedFiles.isEmpty() ? "full" : "incremental";
        meterRegistry.counter("ai.review.files", "mode", mode, "result", "reviewed").increment(reviewed);
        meterRegistry.counter("ai.review.files", "mode", mode, "result", "reused").increment(reused);
    }

    private record ReviewTarget(String filePath, String fileDiff, String fileContent, String baseContent) {}

    /**
     * 같은 PR에서 함께 바뀐 파일 중, 이 파일 코드에 클래스 이름이 등장하는 Java 파일만 고른다.
     * 어떤 메서드를 실제로 호출하는지는 AI 서버가 AST로 다시 걸러내고, 여기서는 메시지가 불필요하게
     * 커지지 않도록 이름이 한 번도 나오지 않는 파일만 뺀다.
     */
    private List<ChangedFileSnapshot> findRelatedChangedFiles(ReviewTarget target, List<ReviewTarget> all) {
        if (!target.filePath().endsWith(".java") || target.fileContent() == null) return List.of();

        List<ChangedFileSnapshot> related = new ArrayList<>();
        for (ReviewTarget other : all) {
            if (other == target || !other.filePath().endsWith(".java") || other.fileContent() == null) continue;
            if (related.size() >= MAX_RELATED_CHANGED_FILES) break;

            String fileName = other.filePath().substring(other.filePath().lastIndexOf('/') + 1);
            String className = fileName.substring(0, fileName.length() - ".java".length());
            if (Pattern.compile("\\b" + Pattern.quote(className) + "\\b").matcher(target.fileContent()).find()) {
                related.add(new ChangedFileSnapshot(other.filePath(), other.fileDiff(), other.fileContent()));
            }
        }
        return related;
    }

    private boolean isReviewableFile(String filePath) {
        return REVIEWABLE_EXTENSIONS.stream().anyMatch(filePath::endsWith);
    }
}
