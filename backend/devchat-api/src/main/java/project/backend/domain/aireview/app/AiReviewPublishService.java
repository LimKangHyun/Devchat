package project.backend.domain.aireview.app;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.backend.domain.aireview.dao.AiCommentModRepository;
import project.backend.domain.aireview.dao.AiReviewCommentRepository;
import project.backend.domain.aireview.dao.AiReviewRepository;
import project.backend.domain.aireview.dto.PublishComment;
import project.backend.domain.aireview.entity.AiReview;
import project.backend.domain.aireview.entity.AiReviewComment;
import project.backend.domain.aireview.entity.PrStatus;
import project.backend.domain.chat.chatroom.entity.ChatRoom;
import project.backend.domain.github.GitRepoUrlUtils;
import project.backend.domain.github.client.GitHubBotClient;
import project.backend.global.exception.errorcode.GitHubErrorCode;
import project.backend.global.exception.ex.GitHubException;
import project.common.dto.FileReviewResult;
import project.common.dto.InlineReview;
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
    private final ObjectMapper objectMapper;

    @Transactional
    public void publishToGitHub(ChatRoom room, Long aiReviewId, String approverUsername) {
        AiReview aiReview = findAiReview(aiReviewId);
        validatePublishable(aiReview);

        GitRepoDto repo = GitRepoUrlUtils.validateAndParseUrl(room.getRepositoryUrl());

        try {
            List<AiReviewComment> allComments = findActiveComments(aiReviewId);
            Set<Long> inactiveCommentIds = resolveInactiveCommentIds(aiReviewId);
            validateActiveComments(allComments, inactiveCommentIds);

            Map<String, Long> commentIdMap = buildCommentIdMap(allComments);
            String fullDiff = gitHubBotClient.getPrDiff(repo.ownerName(), repo.repoName(), aiReview.getPrNumber());
            Map<String, Set<Integer>> diffLineMap = diffParser.parseDiffLines(fullDiff);

            List<PublishComment> comments = buildPublishComments(aiReview, inactiveCommentIds, commentIdMap, diffLineMap);
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

    private Map<String, Long> buildCommentIdMap(List<AiReviewComment> comments) {
        return comments.stream()
                .collect(Collectors.toMap(
                        c -> c.getFilePath() + ":" + c.getLineNumber(),
                        AiReviewComment::getId,
                        (existing, replacement) -> existing
                ));
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

    private List<PublishComment> buildPublishComments(AiReview aiReview, Set<Long> inactiveCommentIds,
                                                      Map<String, Long> commentIdMap,
                                                      Map<String, Set<Integer>> diffLineMap) {
        List<FileReviewResult> files = parseFileResults(aiReview);
        List<PublishComment> comments = new ArrayList<>();

        for (FileReviewResult file : files) {
            Set<Integer> validLines = diffLineMap.getOrDefault(file.filePath(), Set.of());
            for (InlineReview review : file.reviews()) {
                Long commentId = commentIdMap.get(file.filePath() + ":" + review.lineNumber());
                if (commentId != null && inactiveCommentIds.contains(commentId)) continue;
                comments.add(toPublishComment(file.filePath(), review, validLines));
            }
        }
        return comments;
    }

    private PublishComment toPublishComment(String filePath, InlineReview review, Set<Integer> validLines) {
        if (validLines.contains(review.lineNumber())) {
            return new PublishComment(filePath, review.lineNumber(), review.comment());
        }
        int nearestLine = diffParser.findNearestDiffLine(validLines, review.lineNumber());
        return new PublishComment(filePath, nearestLine, "(Line " + review.lineNumber() + ") " + review.comment());
    }

    private List<FileReviewResult> parseFileResults(AiReview aiReview) {
        try {
            Map<String, Object> root = objectMapper.readValue(aiReview.getReviewJson(), new TypeReference<>() {});
            return objectMapper.convertValue(root.get("files"), new TypeReference<List<FileReviewResult>>() {});
        } catch (Exception e) {
            throw new AiReviewException(AiReviewErrorCode.REVIEW_JSON_PARSE_FAILED);
        }
    }
}