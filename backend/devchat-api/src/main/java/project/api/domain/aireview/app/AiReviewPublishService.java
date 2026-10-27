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

            List<PublishComment> inlineComments = new ArrayList<>();
            List<AiReviewComment> generalComments = new ArrayList<>();
            classifyComments(allComments, inactiveCommentIds, diffLineMap, inlineComments, generalComments);

            postReviews(repo, aiReview.getPrNumber(), inlineComments, generalComments, approverUsername);

            aiReview.markAsPublished(approverUsername);
            aiReviewRepository.save(aiReview);

        } catch (AiReviewException e) {
            throw e;
        } catch (Exception e) {
            throw new GitHubException(GitHubErrorCode.GITHUB_API_FAILED);
        }

        log.info("GitHub PR 리뷰 등록 완료: roomId={}, PR #{}, inline={}, general={}",
            room.getId(), aiReview.getPrNumber(),
            0, 0); // 로그는 postReviews 안에서 찍힘
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

    /**
     * 활성 코멘트를 인라인/전체로 분류한다.
     * diff 변경 라인 20줄 이내에 매핑 가능하면 인라인, 아니면 전체 코멘트로 전환한다.
     */
    private void classifyComments(List<AiReviewComment> allComments,
        Set<Long> inactiveCommentIds,
        Map<String, Set<Integer>> diffLineMap,
        List<PublishComment> inlineComments,
        List<AiReviewComment> generalComments) {

        for (AiReviewComment comment : allComments) {
            if (inactiveCommentIds.contains(comment.getId())) continue;

            Set<Integer> validLines = diffLineMap.getOrDefault(comment.getFilePath(), Set.of());
            OptionalInt nearestLine = diffParser.findNearestDiffLine(validLines, comment.getLineNumber());

            if (nearestLine.isPresent()) {
                int mappedLine = nearestLine.getAsInt();
                String body = (mappedLine != comment.getLineNumber())
                    ? "(Line " + comment.getLineNumber() + ") " + comment.getComment()
                    : comment.getComment();
                inlineComments.add(new PublishComment(comment.getFilePath(), mappedLine, body));
            } else {
                generalComments.add(comment);
            }
        }
    }

    private void postReviews(GitRepoDto repo, int prNumber,
        List<PublishComment> inlineComments,
        List<AiReviewComment> generalComments,
        String approverUsername) {

        // 1. 인라인 코멘트 등록
        if (!inlineComments.isEmpty()) {
            gitHubBotClient.postInlineReviews(repo.ownerName(), repo.repoName(), prNumber,
                inlineComments.stream()
                    .map(c -> Map.of("path", (Object) c.path(), "line", c.line(), "body", c.body()))
                    .collect(Collectors.toList()));
            log.info("인라인 리뷰 {}개 등록. PR #{}", inlineComments.size(), prNumber);
        }

        // 2. 전체 코멘트 조립 (매핑 실패한 리뷰 + 승인 메시지)
        StringBuilder body = new StringBuilder();
        body.append("✅ AI 리뷰가 @").append(approverUsername).append(" 에 의해 등록되었습니다.");

        if (!generalComments.isEmpty()) {
            body.append("\n\n---\n\n");
            body.append("📝 **인라인 매핑 불가 리뷰** (변경 라인에서 20줄 이상 떨어진 코멘트)\n\n");
            for (AiReviewComment comment : generalComments) {
                body.append("- **").append(comment.getFilePath())
                    .append(":").append(comment.getLineNumber()).append("** — ")
                    .append(comment.getComment()).append("\n");
            }
            log.info("전체 코멘트로 전환된 리뷰 {}개. PR #{}", generalComments.size(), prNumber);
        }

        gitHubBotClient.postReviewComment(repo.ownerName(), repo.repoName(), prNumber, body.toString());
    }
}