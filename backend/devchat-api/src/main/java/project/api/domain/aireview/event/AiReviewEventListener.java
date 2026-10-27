package project.api.domain.aireview.event;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import project.api.domain.aireview.app.AiReviewDiffParser;
import project.api.domain.aireview.app.AiReviewService;
import project.api.domain.aireview.entity.AiReview;
import project.api.domain.github.GitRepoUrlUtils;
import project.api.domain.github.client.GitHubBotClient;
import project.api.global.redis.RedisStreamClient;
import project.common.dto.github.GitRepoDto;
import project.common.message.aireview.ChangedFileSnapshot;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiReviewEventListener {

    private static final int MAX_CHANGED_FILES = 20;
    private static final int MAX_TOTAL_DIFF_LINES = 2500;
    private static final int MAX_FILE_DIFF_LINES = 250;
    /** 리뷰 요청 하나에 같이 보내는 다른 변경 파일 수 상한. 메시지 크기를 제한한다. */
    private static final int MAX_RELATED_CHANGED_FILES = 5;

    private final AiReviewService aiReviewService;
    private final AiReviewDiffParser diffParser;
    private final GitHubBotClient gitHubBotClient;
    private final RedisStreamClient redisStreamClient;
    private final MeterRegistry meterRegistry;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("chatBroadcastExecutor")
    public void handleAiReviewRequested(AiReviewRequestedEvent event) {
        AiReview aiReview = aiReviewService.findByIdWithChatRoom(event.aiReviewId());
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
        //    파일 A를 리뷰할 때 같은 PR에서 바뀐 파일 B의 새 코드가 필요하므로, 발행 전에 전부 모아야 한다.
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
                    repo.ownerName(), repo.repoName(), filePath, event.headSha());
            String baseContent = "added".equals(status) ? ""
                    : gitHubBotClient.getFileContent(
                    repo.ownerName(), repo.repoName(), filePath, event.baseSha());

            targets.add(new ReviewTarget(filePath, fileDiff, fileContent, baseContent));
        }

        // 2) 증분 리뷰면 이전 결과를 재사용하는 파일은 요청하지 않는다.
        //    재사용 파일도 위에서 코드를 가져왔으므로, 다른 파일을 리뷰할 때 "같이 바뀐 파일" 문맥은 전체 리뷰와 같다.
        Set<String> reused = event.reusedFiles() == null ? Set.of() : event.reusedFiles();
        List<ReviewTarget> toReview = targets.stream().filter(t -> !reused.contains(t.filePath())).toList();
        recordFileCounts(reused.isEmpty() ? "full" : "incremental", toReview.size(), targets.size() - toReview.size());
        if (!reused.isEmpty()) {
            log.info("[증분 리뷰] aiReviewId={}, 다시 리뷰 {}개, 재사용 {}개",
                    aiReview.getId(), toReview.size(), targets.size() - toReview.size());
        }

        if (toReview.isEmpty()) {
            aiReviewService.completeWithoutNewFiles(aiReview.getId(), !reused.isEmpty());
            return;
        }

        // 결과가 먼저 도착해도 완료 판정이 되도록 총 파일 수를 발행 전에 기록한다
        aiReviewService.updateTotalFiles(aiReview.getId(), toReview.size());

        // 3) 파일마다 리뷰 요청을 발행한다.
        Map<String, String> fileContentMap = new HashMap<>();
        for (ReviewTarget target : toReview) {
            fileContentMap.put(target.filePath(), target.fileContent());

            redisStreamClient.publishAiReviewRequest(
                    aiReview.getId(),
                    aiReview.getChatRoom().getId(),
                    aiReview.getChatRoom().getId(),
                    target.filePath(),
                    target.fileDiff(),
                    target.fileContent(),
                    target.baseContent(),
                    aiReview.getPrTitle(),
                    aiReview.getPrBody(),
                    changedFilesInPr,
                    findRelatedChangedFiles(target, targets),
                    aiReview.getChatRoom().getReviewConventions(),
                    aiReview.getCommitSha()
            );
        }

        aiReviewService.saveFileContents(aiReview.getId(), fileContentMap, reused);
    }

    /**
     * 리뷰 요청한 파일과 재사용한 파일 수. 재사용 파일 하나가 LLM 호출 하나를 아낀 것이다.
     * Grafana: sum(ai_review_files_total{result="reused"}) / sum(ai_review_files_total)
     */
    private void recordFileCounts(String mode, int reviewed, int reused) {
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

    private static final Set<String> REVIEWABLE_EXTENSIONS = Set.of(
            ".java", ".kt", ".py", ".js", ".ts", ".tsx", ".jsx"
    );

    private boolean isReviewableFile(String filePath) {
        return REVIEWABLE_EXTENSIONS.stream().anyMatch(filePath::endsWith);
    }
}