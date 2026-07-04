package project.api.domain.aireview.app;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.api.domain.aireview.dao.AiCommentModRepository;
import project.api.domain.aireview.dao.AiReviewCommentRepository;
import project.api.domain.aireview.dao.AiReviewRepository;
import project.api.domain.aireview.dto.PublishComment;
import project.api.domain.aireview.entity.AiReview;
import project.api.domain.aireview.entity.AiReviewComment;
import project.api.domain.aireview.entity.PrStatus;
import project.api.domain.chat.chatroom.entity.ChatRoom;
import project.api.domain.github.GitRepoUrlUtils;
import project.api.domain.github.client.GitHubBotClient;
import project.api.global.exception.errorcode.GitHubErrorCode;
import project.api.global.exception.ex.GitHubException;
import project.common.dto.github.GitRepoDto;
import project.common.exception.errorcode.AiReviewErrorCode;
import project.common.exception.ex.AiReviewException;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiReviewPublishService {

    private final AiReviewRepository aiReviewRepository;
    private final AiReviewCommentRepository aiReviewCommentRepository;
    private final AiCommentModRepository aiCommentModRepository;
    private final GitHubBotClient gitHubBotClient;
    private final AiReviewDiffParser diffParser;

    @Transactional
    public void publishToGitHub(ChatRoom room, Long aiReviewId, String approverUsername) {
        AiReview aiReview = findAiReview(aiReviewId);
        validatePublishable(aiReview);

        GitRepoDto repo = GitRepoUrlUtils.validateAndParseUrl(room.getRepositoryUrl());

        try {
            List<AiReviewComment> allComments = findActiveComments(aiReviewId);
            Set<Long> inactiveCommentIds = resolveInactiveCommentIds(aiReviewId);
            validateActiveComments(allComments, inactiveCommentIds);

            String fullDiff = gitHubBotClient.getPrDiff(repo.ownerName(), repo.repoName(), aiReview.getPrNumber());
            Map<String, Set<Integer>> diffLineMap = diffParser.parseDiffLines(fullDiff);

            List<PublishComment> comments = buildPublishComments(allComments, inactiveCommentIds, diffLineMap);
            postReviews(repo, aiReview.getPrNumber(), comments, approverUsername);

            aiReview.markAsPublished(approverUsername);
            aiReviewRepository.save(aiReview);

        } catch (AiReviewException e) {
            throw e;
        } catch (Exception e) {
            throw new GitHubException(GitHubErrorCode.GITHUB_API_FAILED);
        }

        log.info("GitHub PR 인라인 리뷰 등록 완료: roomId={}, PR #{}", room.getId(), aiReview.getPrNumber());
    }

    private AiReview findAiReview(Long aiReviewId) {
        return aiReviewRepository.findById(aiReviewId)
                .orElseThrow(() -> new AiReviewException(AiReviewErrorCode.AI_REVIEW_NOT_FOUND));
    }

    private void validatePublishable(AiReview aiReview) {
        if (aiReview.isGithubPublished()) throw new AiReviewException(AiReviewErrorCode.ALREADY_PUBLISHED);
        if (aiReview.getPrStatus() != PrStatus.OPEN) throw new AiReviewException(AiReviewErrorCode.PR_NOT_OPEN);
    }

    private List<AiReviewComment> findActiveComments(Long aiReviewId) {
        List<AiReviewComment> comments = aiReviewCommentRepository.findByAiReview_Id(aiReviewId);
        if (comments.isEmpty()) throw new AiReviewException(AiReviewErrorCode.NO_REVIEWS);
        return comments;
    }

    private void validateActiveComments(List<AiReviewComment> allComments, Set<Long> inactiveCommentIds) {
        long activeCount = allComments.stream()
                .filter(c -> !inactiveCommentIds.contains(c.getId()))
                .count();
        if (activeCount == 0) throw new AiReviewException(AiReviewErrorCode.NO_ACTIVE_REVIEWS);
    }

    private Set<Long> resolveInactiveCommentIds(Long aiReviewId) {
        return aiCommentModRepository.findLatestStatusesByAiReviewId(aiReviewId).stream()
                .filter(s -> !s.isActive())
                .map(s -> s.getComment().getId())
                .collect(Collectors.toSet());
    }

    private void postReviews(GitRepoDto repo, int prNumber, List<PublishComment> comments, String approverUsername) {
        if (!comments.isEmpty()) {
            gitHubBotClient.postInlineReviews(repo.ownerName(), repo.repoName(), prNumber,
                    comments.stream()
                            .map(c -> Map.of("path", (Object) c.path(), "line", c.line(), "body", c.body()))
                            .collect(Collectors.toList()));
        }
        gitHubBotClient.postReviewComment(repo.ownerName(), repo.repoName(), prNumber,
                "✅ AI 리뷰가 @" + approverUsername + " 에 의해 등록되었습니다.");
    }

    private List<PublishComment> buildPublishComments(List<AiReviewComment> allComments,
                                                      Set<Long> inactiveCommentIds,
                                                      Map<String, Set<Integer>> diffLineMap) {
        List<PublishComment> comments = new ArrayList<>();

        for (AiReviewComment comment : allComments) {
            if (inactiveCommentIds.contains(comment.getId())) continue;
            Set<Integer> validLines = diffLineMap.getOrDefault(comment.getFilePath(), Set.of());
            comments.add(toPublishComment(comment, validLines));
        }
        return comments;
    }

    private PublishComment toPublishComment(AiReviewComment comment, Set<Integer> validLines) {
        int lineNumber = comment.getLineNumber();
        if (validLines.contains(lineNumber)) {
            return new PublishComment(comment.getFilePath(), lineNumber, comment.getComment());
        }
        int nearestLine = diffParser.findNearestDiffLine(validLines, lineNumber);
        return new PublishComment(comment.getFilePath(), nearestLine, "(Line " + lineNumber + ") " + comment.getComment());
    }
}