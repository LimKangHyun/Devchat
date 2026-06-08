package project.backend.domain.github.app;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.backend.domain.chat.chatmessage.dao.ChatMessageRepository;
import project.backend.domain.chat.chatmessage.dto.ChatMessageResponse;
import project.backend.domain.chat.chatmessage.entity.ChatMessage;
import project.backend.domain.chat.chatmessage.entity.MessageType;
import project.backend.domain.chat.chatmessage.mapper.ChatMessageMapper;
import project.backend.domain.chat.chatroom.dao.ChatRoomRedisRepository;
import project.backend.domain.chat.chatroom.entity.ChatRoom;
import project.backend.domain.github.GeminiClient;
import project.backend.domain.github.GitHubBotClient;
import project.backend.domain.github.GitRepoUrlUtils;
import project.backend.domain.github.dao.AiCommentModRepository;
import project.backend.domain.github.dao.AiReviewCommentRepository;
import project.backend.domain.github.dao.AiReviewRepository;
import project.backend.domain.github.dto.AiReviewResponse;
import project.backend.domain.github.dto.GitRepoDto;
import project.backend.domain.github.entity.*;
import project.backend.domain.member.app.MemberService;
import project.backend.domain.member.entity.Member;
import project.backend.global.exception.errorcode.AiReviewErrorCode;
import project.backend.global.exception.errorcode.GitHubErrorCode;
import project.backend.global.exception.ex.AiReviewException;
import project.backend.global.exception.ex.GitHubException;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiReviewService {

    private final GitHubBotClient gitHubBotClient;
    private final GeminiClient geminiClient;
    private final ChatMessageRepository chatMessageRepository;
    private final AiReviewRepository aiReviewRepository;
    private final ChatMessageMapper chatMessageMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final MemberService memberService;
    private final ChatRoomRedisRepository chatRoomRedisRepository;
    private final AiReviewCommentRepository aiReviewCommentRepository;
    private final AiCommentModRepository aiCommentModRepository;
    private final AiReviewCommentSaveService aiReviewCommentSaveService;
    private final ObjectMapper objectMapper;

    @Async("chatBroadcastExecutor")
    public void triggerAiReview(ChatRoom room, int prNumber, String headSha, String baseSha) {
        if (!room.isAiReviewEnabled()) return;

        AiReview aiReview = findAiReview(room.getId(), prNumber);

        if (aiReview.getStatus() == AiReviewStatus.FAIL) {
            aiReview.resetToPending(headSha);
            aiReviewRepository.save(aiReview);
        }

        broadcastAiReviewStatus(room.getId(), aiReview);

        try {
            GitRepoDto repo = GitRepoUrlUtils.validateAndParseUrl(room.getRepositoryUrl());
            String fullDiff = gitHubBotClient.getPrDiff(repo.ownerName(), repo.repoName(), prNumber);
            List<Map<String, Object>> fileResults = buildFileResults(repo, fullDiff, headSha, baseSha,
                    aiReview.getPrTitle(), aiReview.getPrBody());

            if (fileResults.isEmpty()) {
                aiReview.updateFail("모든 파일 리뷰 생성 실패");
            } else {
                aiReview.updateSuccess(objectMapper.writeValueAsString(Map.of("files", fileResults)));
                aiReviewCommentSaveService.saveComments(aiReview, fileResults);
            }
        } catch (Exception e) {
            log.error("AI 리뷰 생성 실패: roomId={}, PR #{}", room.getId(), prNumber, e);
            aiReview.updateFail(e.getMessage());
        }

        aiReviewRepository.save(aiReview);
        broadcastAiReviewStatus(room.getId(), aiReview);
    }

    @Transactional
    public void toggleComment(Long commentId, InactiveReason reason, String otherReason, String changedBy) {
        AiReviewComment comment = aiReviewCommentRepository.findById(commentId)
                .orElseThrow(() -> new AiReviewException(AiReviewErrorCode.COMMENT_NOT_FOUND));

        if (comment.getAiReview().isGithubPublished()) {
            throw new AiReviewException(AiReviewErrorCode.ALREADY_PUBLISHED);
        }

        AiCommentMod latest = aiCommentModRepository
                .findTopByComment_IdOrderByCreatedAtDesc(commentId)
                .orElseThrow(() -> new AiReviewException(AiReviewErrorCode.COMMENT_STATUS_NOT_FOUND));

        boolean newActive = !latest.isActive();

        if (!newActive && reason == null) {
            throw new AiReviewException(AiReviewErrorCode.INACTIVE_REASON_REQUIRED);
        }
        if (!newActive && reason == InactiveReason.OTHER && (otherReason == null || otherReason.isBlank())) {
            throw new AiReviewException(AiReviewErrorCode.OTHER_REASON_REQUIRED);
        }

        AiCommentMod newStatus = AiCommentMod.builder()
                .comment(comment)
                .active(newActive)
                .reason(newActive ? null : reason)
                .otherReason(newActive ? null : otherReason)
                .changedBy(changedBy)
                .createdAt(LocalDateTime.now())
                .build();
        aiCommentModRepository.save(newStatus);
    }

    @Transactional(readOnly = true)
    public AiReviewResponse getAiReview(Long aiReviewId) {
        AiReview aiReview = aiReviewRepository.findById(aiReviewId)
                .orElseThrow(() -> new AiReviewException(AiReviewErrorCode.AI_REVIEW_NOT_FOUND));

        Map<Long, AiCommentMod> statusMap = aiCommentModRepository
                .findLatestStatusesByAiReviewId(aiReviewId).stream()
                .collect(Collectors.toMap(s -> s.getComment().getId(), s -> s));

        List<AiReviewComment> comments = aiReviewCommentRepository.findByAiReview_Id(aiReviewId);
        Map<String, Object> json = parseReviewJson(aiReview.getReviewJson());
        List<Map<String, Object>> files = (List<Map<String, Object>>) json.get("files");

        for (Map<String, Object> file : files) {
            String filePath = (String) file.get("filePath");
            List<Map<String, Object>> reviews = (List<Map<String, Object>>) file.get("reviews");

            for (Map<String, Object> review : reviews) {
                int lineNumber = ((Number) review.get("lineNumber")).intValue();
                comments.stream()
                        .filter(c -> c.getFilePath().equals(filePath) && c.getLineNumber() == lineNumber)
                        .findFirst()
                        .ifPresent(c -> {
                            AiCommentMod status = statusMap.get(c.getId());
                            if (status != null) {
                                review.put("commentId", c.getId());
                                review.put("active", status.isActive());
                                review.put("reason", status.getReason() != null ? status.getReason().name() : null);
                                review.put("otherReason", status.getOtherReason());
                                review.put("changedBy", status.getChangedBy());
                            }
                        });
            }
        }

        try {
            return new AiReviewResponse(
                    objectMapper.writeValueAsString(json),
                    aiReview.isGithubPublished(),
                    aiReview.getPublishedBy(),
                    aiReview.getPrTitle(),
                    aiReview.getPrBody()
            );
        } catch (Exception e) {
            throw new AiReviewException(AiReviewErrorCode.REVIEW_JSON_SERIALIZE_FAILED);
        }
    }

    @Transactional
    public AiReview createPendingAndMessage(ChatRoom room, int prNumber, String headSha,
                                            String githubBotUsername, String prTitle, String prBody) {
        AiReview aiReview = AiReview.builder()
                .chatRoom(room)
                .prNumber(prNumber)
                .commitSha(headSha)
                .status(AiReviewStatus.PENDING)
                .prTitle(prTitle)
                .prBody(prBody)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        aiReviewRepository.save(aiReview);

        Member githubBot = memberService.getMemberByUsername(githubBotUsername);
        chatRoomRedisRepository.genMessageSeq(room.getId());
        ChatMessage message = chatMessageMapper.toAiReviewMessageEntity(aiReview, githubBot, room);
        chatMessageRepository.save(message);
        log.info("저장된 message aiReviewId: {}", message.getAiReview() != null ? message.getAiReview().getId() : null);

        ChatMessageResponse response = chatMessageMapper.toAiReviewResponse(message);
        messagingTemplate.convertAndSend("/topic/chat/" + room.getId(), response);

        return aiReview;
    }

    public void publishToGitHub(ChatRoom room, Long aiReviewId, String approveUsername) {
        AiReview aiReview = aiReviewRepository.findById(aiReviewId)
                .orElseThrow(() -> new AiReviewException(AiReviewErrorCode.AI_REVIEW_NOT_FOUND));

        if (aiReview.isGithubPublished()) {
            throw new AiReviewException(AiReviewErrorCode.ALREADY_PUBLISHED);
        }
        if (aiReview.getPrStatus() != PrStatus.OPEN) {
            throw new AiReviewException(AiReviewErrorCode.PR_NOT_OPEN);
        }

        GitRepoDto repo = GitRepoUrlUtils.validateAndParseUrl(room.getRepositoryUrl());

        try {
            Map<String, Object> json = parseReviewJson(aiReview.getReviewJson());
            List<Map<String, Object>> files = (List<Map<String, Object>>) json.get("files");

            List<AiReviewComment> allComments = aiReviewCommentRepository.findByAiReview_Id(aiReviewId);
            if (allComments.isEmpty()) {
                throw new AiReviewException(AiReviewErrorCode.NO_REVIEWS);
            }

            List<AiCommentMod> latestStatuses = aiCommentModRepository.findLatestStatusesByAiReviewId(aiReviewId);
            Set<Long> inactiveCommentIds = latestStatuses.stream()
                    .filter(s -> !s.isActive())
                    .map(s -> s.getComment().getId())
                    .collect(Collectors.toSet());

            long activeCount = allComments.stream()
                    .filter(c -> !inactiveCommentIds.contains(c.getId()))
                    .count();
            if (activeCount == 0) {
                throw new AiReviewException(AiReviewErrorCode.NO_ACTIVE_REVIEWS);
            }

            Map<String, Long> commentIdMap = allComments.stream()
                    .collect(Collectors.toMap(
                            c -> c.getFilePath() + ":" + c.getLineNumber(),
                            AiReviewComment::getId,
                            (existing, replacement) -> existing
                    ));

            String fullDiff = gitHubBotClient.getPrDiff(repo.ownerName(), repo.repoName(), aiReview.getPrNumber());
            Map<String, Set<Integer>> diffLineMap = parseDiffLines(fullDiff);
            List<Map<String, Object>> comments = buildPublishComments(files, inactiveCommentIds, commentIdMap, diffLineMap);

            if (!comments.isEmpty()) {
                gitHubBotClient.postInlineReviews(repo.ownerName(), repo.repoName(), aiReview.getPrNumber(), comments);
            }

            gitHubBotClient.postReviewComment(
                    repo.ownerName(), repo.repoName(), aiReview.getPrNumber(),
                    "✅ AI 리뷰가 @" + approveUsername + " 에 의해 등록되었습니다."
            );

            aiReview.markAsPublished(approveUsername);
            aiReviewRepository.save(aiReview);

        } catch (AiReviewException e) {
            throw e;
        } catch (Exception e) {
            throw new GitHubException(GitHubErrorCode.GITHUB_API_FAILED);
        }

        log.info("GitHub PR 인라인 리뷰 등록 완료: roomId={}, PR #{}", room.getId(), aiReview.getPrNumber());
    }

    @Transactional
    public void updatePrStatus(Long roomId, int prNumber, PrStatus prStatus) {
        aiReviewRepository.findByChatRoom_IdAndPrNumber(roomId, prNumber)
                .ifPresent(review -> {
                    review.updatePrStatus(prStatus);
                    aiReviewRepository.save(review);
                    broadcastAiReviewStatus(roomId, review);
                });
    }

    @Transactional
    public void updatePrInfo(Long roomId, int prNumber, String prTitle, String prBody) {
        aiReviewRepository.findByChatRoom_IdAndPrNumber(roomId, prNumber)
                .ifPresent(review -> {
                    review.updatePrInfo(prTitle, prBody);
                    aiReviewRepository.save(review);
                });
    }

    public void deleteByRoomId(Long roomId) {
        aiCommentModRepository.deleteByComment_AiReview_ChatRoom_Id(roomId);
        aiReviewCommentRepository.deleteByAiReview_ChatRoom_Id(roomId);
        aiReviewRepository.deleteByChatRoom_Id(roomId);
    }

    private AiReview findAiReview(Long roomId, int prNumber) {
        return aiReviewRepository.findByChatRoom_IdAndPrNumber(roomId, prNumber)
                .orElseThrow(() -> new AiReviewException(AiReviewErrorCode.AI_REVIEW_NOT_FOUND));
    }

    private List<Map<String, Object>> buildFileResults(GitRepoDto repo, String fullDiff,
                                                       String headSha, String baseSha,
                                                       String prTitle, String prBody) {
        Map<String, String> fileDiffs = parseFileDiffs(fullDiff);
        List<Map<String, Object>> fileResults = new ArrayList<>();

        for (Map.Entry<String, String> entry : fileDiffs.entrySet()) {
            String filePath = entry.getKey();
            try {
                String fileContent = gitHubBotClient.getFileContent(repo.ownerName(), repo.repoName(), filePath, headSha);
                String baseContent = gitHubBotClient.getFileContent(repo.ownerName(), repo.repoName(), filePath, baseSha);
                List<Map<String, Object>> reviews = geminiClient.reviewPrDiffInline(entry.getValue(), fileContent, prTitle, prBody);
                fileResults.add(Map.of("filePath", filePath, "baseContent", baseContent, "fileContent", fileContent, "reviews", reviews));
            } catch (Exception e) {
                log.error("파일 리뷰 실패: {}", filePath, e);
            }
        }
        return fileResults;
    }

    private List<Map<String, Object>> buildPublishComments(List<Map<String, Object>> files,
                                                           Set<Long> inactiveCommentIds,
                                                           Map<String, Long> commentIdMap,
                                                           Map<String, Set<Integer>> diffLineMap) {
        List<Map<String, Object>> comments = new ArrayList<>();
        for (Map<String, Object> file : files) {
            String filePath = (String) file.get("filePath");
            List<Map<String, Object>> reviews = (List<Map<String, Object>>) file.get("reviews");
            if (reviews == null || reviews.isEmpty()) continue;

            Set<Integer> validLines = diffLineMap.getOrDefault(filePath, Set.of());

            for (Map<String, Object> review : reviews) {
                int lineNumber = ((Number) review.get("lineNumber")).intValue();
                Long commentId = commentIdMap.get(filePath + ":" + lineNumber);
                if (commentId != null && inactiveCommentIds.contains(commentId)) continue;

                String comment = (String) review.get("comment");
                if (validLines.contains(lineNumber)) {
                    comments.add(Map.of("path", filePath, "line", lineNumber, "body", comment));
                } else {
                    comments.add(Map.of(
                            "path", filePath,
                            "line", findNearestDiffLine(validLines, lineNumber),
                            "body", "(Line " + lineNumber + ") " + comment
                    ));
                }
            }
        }
        return comments;
    }

    private void broadcastAiReviewStatus(Long roomId, AiReview aiReview) {
        chatMessageRepository
                .findByChatRoom_IdAndPrNumberAndType(roomId, aiReview.getPrNumber(), MessageType.AI_REVIEW)
                .ifPresent(message -> {
                    ChatMessageResponse response = ChatMessageResponse.builder()
                            .messageId(message.getId())
                            .type(message.getType())
                            .prNumber(aiReview.getPrNumber())
                            .aiReviewId(aiReview.getId())
                            .aiReviewStatus(aiReview.getStatus().name())
                            .prStatus(aiReview.getPrStatus().name())
                            .senderId(message.getSender().getId())
                            .senderName(message.getSender().getNickname())
                            .profileImageUrl(message.getSender().getProfileImage())
                            .createdAt(message.getCreatedAt())
                            .build();
                    messagingTemplate.convertAndSend("/topic/chat/" + roomId, response);
                });
    }

    private Map<String, Set<Integer>> parseDiffLines(String fullDiff) {
        Map<String, Set<Integer>> result = new LinkedHashMap<>();
        String currentFile = null;
        int headLineNum = 0;

        for (String line : fullDiff.split("\n")) {
            if (line.startsWith("diff --git")) {
                String[] parts = line.split(" b/");
                if (parts.length > 1) {
                    currentFile = parts[1].trim();
                    result.put(currentFile, new LinkedHashSet<>());
                }
            } else if (line.startsWith("@@ ")) {
                Matcher m = Pattern.compile("\\+([0-9]+)").matcher(line);
                if (m.find()) headLineNum = Integer.parseInt(m.group(1)) - 1;
            } else if (currentFile != null) {
                if (line.startsWith("+") && !line.startsWith("+++")) {
                    headLineNum++;
                    result.get(currentFile).add(headLineNum);
                } else if (!line.startsWith("-")) {
                    headLineNum++;
                }
            }
        }
        return result;
    }

    private int findNearestDiffLine(Set<Integer> validLines, int target) {
        return validLines.stream()
                .min(Comparator.comparingInt(l -> Math.abs(l - target)))
                .orElse(target);
    }

    private Map<String, String> parseFileDiffs(String fullDiff) {
        Map<String, String> fileDiffs = new LinkedHashMap<>();
        String[] parts = fullDiff.split("(?=diff --git )");
        for (String part : parts) {
            if (part.isBlank()) continue;
            Matcher matcher = Pattern.compile("diff --git a/.+ b/(.+)").matcher(part);
            if (matcher.find()) fileDiffs.put(matcher.group(1).trim(), part);
        }
        return fileDiffs;
    }

    private Map<String, Object> parseReviewJson(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            throw new AiReviewException(AiReviewErrorCode.REVIEW_JSON_PARSE_FAILED);
        }
    }
}