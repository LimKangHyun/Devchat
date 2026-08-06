package project.api.domain.aireview.event;

import java.util.List;
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

    private final AiReviewService aiReviewService;
    private final AiReviewDiffParser diffParser;
    private final GitHubBotClient gitHubBotClient;
    private final RedisStreamClient redisStreamClient;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("chatBroadcastExecutor")
    public void handleAiReviewRequested(AiReviewRequestedEvent event) {
        AiReview aiReview = aiReviewService.findByIdWithChatRoom(event.aiReviewId());
        GitRepoDto repo = GitRepoUrlUtils.validateAndParseUrl(aiReview.getChatRoom().getRepositoryUrl());

        Map<String, String> fileDiffs = diffParser.parseFileDiffs(aiReview.getPrDiff());
        Map<String, String> fileStatuses = gitHubBotClient.getPrFileStatuses(
                repo.ownerName(), repo.repoName(), aiReview.getPrNumber());

        // 실제 리뷰 대상 파일만 먼저 걸러냄 (deleted 제외, 리뷰 가능 확장자만)
        Map<String, String> reviewableFileDiffs = fileDiffs.entrySet().stream()
                .filter(e -> !"deleted".equals(fileStatuses.getOrDefault(e.getKey(), "modified")))
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

        int totalFiles = 0;
        Map<String, String> fileContentMap = new HashMap<>();

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

            fileContentMap.put(filePath, fileContent);
            totalFiles++;

            redisStreamClient.publishAiReviewRequest(
                    aiReview.getId(),
                    aiReview.getChatRoom().getId(),
                    aiReview.getChatRoom().getId(),
                    filePath,
                    fileDiff,
                    fileContent,
                    baseContent,
                    aiReview.getPrTitle(),
                    aiReview.getPrBody(),
                    changedFilesInPr
            );
        }

        aiReviewService.updateTotalFiles(aiReview.getId(), totalFiles);

        if (!fileContentMap.isEmpty()) {
            aiReviewService.saveFileContents(aiReview.getId(), fileContentMap);
        }
    }

    private static final Set<String> REVIEWABLE_EXTENSIONS = Set.of(
            ".java", ".kt", ".py", ".js", ".ts", ".tsx", ".jsx"
    );

    private boolean isReviewableFile(String filePath) {
        return REVIEWABLE_EXTENSIONS.stream().anyMatch(filePath::endsWith);
    }
}