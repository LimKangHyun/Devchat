package project.backend.domain.aireview.event;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import project.backend.domain.aireview.app.AiReviewDiffParser;
import project.backend.domain.aireview.app.AiReviewService;
import project.backend.domain.aireview.entity.AiReview;
import project.backend.domain.github.GitRepoUrlUtils;
import project.backend.domain.github.client.GitHubBotClient;
import project.backend.global.redis.RedisStreamClient;
import project.common.dto.github.GitRepoDto;

import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class AiReviewEventListener {

    private final AiReviewService aiReviewService;
    private final AiReviewDiffParser diffParser;

    private final GitHubBotClient gitHubBotClient;
    private final RedisStreamClient redisStreamClient;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("chatBroadcastExecutor")
    public void handleAiReviewRequested(AiReviewRequestedEvent event) {
        AiReview aiReview = aiReviewService.findById(event.aiReviewId());
        GitRepoDto repo = GitRepoUrlUtils.validateAndParseUrl(aiReview.getChatRoom().getRepositoryUrl());

        Map<String, String> fileDiffs = diffParser.parseFileDiffs(aiReview.getPrDiff());
        Map<String, String> fileStatuses = gitHubBotClient.getPrFileStatuses(
                repo.ownerName(), repo.repoName(), aiReview.getPrNumber());

        int totalFiles = 0;

        for (Map.Entry<String, String> entry : fileDiffs.entrySet()) {
            String filePath = entry.getKey();
            String fileDiff = entry.getValue();
            String status = fileStatuses.getOrDefault(filePath, "modified");

            if ("deleted".equals(status)) continue;
            if (!isReviewableFile(filePath)) continue;

            String fileContent = gitHubBotClient.getFileContent(
                    repo.ownerName(), repo.repoName(), filePath, event.headSha());
            String baseContent = "added".equals(status) ? ""
                    : gitHubBotClient.getFileContent(
                    repo.ownerName(), repo.repoName(), filePath, event.baseSha());

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
                    aiReview.getPrBody()
            );
        }

        aiReviewService.updateTotalFiles(aiReview.getId(), totalFiles);
    }

    private static final Set<String> REVIEWABLE_EXTENSIONS = Set.of(
            ".java", ".kt", ".py", ".js", ".ts", ".tsx", ".jsx"
    );

    private boolean isReviewableFile(String filePath) {
        return REVIEWABLE_EXTENSIONS.stream().anyMatch(filePath::endsWith);
    }
}