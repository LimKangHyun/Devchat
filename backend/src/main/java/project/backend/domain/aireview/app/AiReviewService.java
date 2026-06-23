package project.backend.domain.aireview.app;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.backend.domain.aireview.client.GeminiClient;
import project.backend.domain.aireview.dao.AiCommentModRepository;
import project.backend.domain.aireview.dao.AiReviewCommentRepository;
import project.backend.domain.aireview.dao.AiReviewRepository;
import project.backend.domain.aireview.dto.AiReviewResponse;
import project.backend.domain.aireview.dto.FileReviewResult;
import project.backend.domain.aireview.dto.InlineReview;
import project.backend.domain.aireview.dto.PrReviewContext;
import project.backend.domain.aireview.entity.*;
import project.backend.domain.chat.chatmessage.dao.ChatMessageRepository;
import project.backend.domain.chat.chatmessage.dto.ChatMessageResponse;
import project.backend.domain.chat.chatmessage.entity.ChatMessage;
import project.backend.domain.chat.chatmessage.entity.MessageType;
import project.backend.domain.chat.chatmessage.mapper.ChatMessageMapper;
import project.backend.domain.chat.chatroom.dao.ChatRoomRedisRepository;
import project.backend.domain.chat.chatroom.entity.ChatRoom;
import project.backend.domain.github.GitRepoUrlUtils;
import project.backend.domain.github.client.GitHubBotClient;
import project.backend.domain.github.dto.GitRepoDto;
import project.backend.domain.member.app.MemberService;
import project.backend.domain.member.entity.Member;
import project.backend.global.exception.errorcode.AiReviewErrorCode;
import project.backend.global.exception.ex.AiReviewException;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiReviewService {

    private static final int MAX_CHANGED_FILES = 10;
    private static final int MAX_TOTAL_DIFF_LINES = 2500;
    private static final int MAX_FILE_DIFF_LINES = 250;

    private final GitHubBotClient gitHubBotClient;
    private final GeminiClient geminiClient;
    private final ChatMessageRepository chatMessageRepository;
    private final AiReviewRepository aiReviewRepository;
    private final AiReviewCommentRepository aiReviewCommentRepository;
    private final AiCommentModRepository aiCommentModRepository;
    private final ChatMessageMapper chatMessageMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final MemberService memberService;
    private final RagContextService ragContextService;
    private final ChatRoomRedisRepository chatRoomRedisRepository;
    private final AiReviewDiffParser diffParser;
    private final ObjectMapper objectMapper;

    private static final Set<String> REVIEWABLE_EXTENSIONS = Set.of(
            ".java", ".kt", ".py", ".js", ".ts", ".tsx", ".jsx"
    );

    @Transactional
    public void triggerAiReview(ChatRoom room, int prNumber, String headSha, String baseSha) {
        if (!room.isAiReviewEnabled()) return;

        AiReview aiReview = findAiReview(room.getId(), prNumber);

        if (!headSha.equals(aiReview.getCommitSha())) {
            aiCommentModRepository.deleteByComment_AiReview_Id(aiReview.getId());
            aiReviewCommentRepository.deleteByAiReview_Id(aiReview.getId());
            aiReview.resetToPending(headSha);
            aiReviewRepository.save(aiReview);
        } else {
            resetIfFailed(aiReview, headSha);
        }
        broadcastAiReviewStatus(room.getId(), aiReview);

        try {
            GitRepoDto repo = GitRepoUrlUtils.validateAndParseUrl(room.getRepositoryUrl());
            String fullDiff = gitHubBotClient.getPrDiff(repo.ownerName(), repo.repoName(), prNumber);

            if (isSkipped(aiReview, room.getId(), fullDiff)) return;

            PrReviewContext ctx = new PrReviewContext(repo, fullDiff, headSha, baseSha, room.getId());
            List<FileReviewResult> fileResults = buildFileResults(ctx, aiReview);

            if (fileResults.isEmpty()) {
                aiReview.updateFail("모든 파일 리뷰 생성 실패");
            } else {
                aiReview.updateSuccess(objectMapper.writeValueAsString(Map.of("files", fileResults)));
                saveComments(aiReview, fileResults);
            }
        } catch (Exception e) {
            log.error("AI 리뷰 생성 실패: roomId={}, PR #{}", room.getId(), prNumber, e);
            aiReview.updateFail(e.getMessage());
        }

        aiReviewRepository.save(aiReview);
        broadcastAiReviewStatus(room.getId(), aiReview);
    }

    private void resetIfFailed(AiReview aiReview, String headSha) {
        if (aiReview.getStatus() == AiReviewStatus.FAIL) {
            aiReview.resetToPending(headSha);
            aiReviewRepository.save(aiReview);
        }
    }

    private boolean isSkipped(AiReview aiReview, Long roomId, String fullDiff) {
        List<String> changedFiles = diffParser.parseChangedFiles(fullDiff);
        if (changedFiles.size() > MAX_CHANGED_FILES) {
            skipAndBroadcast(aiReview, roomId, "변경 파일 수 초과 (" + changedFiles.size() + "개)");
            return true;
        }
        long totalLines = fullDiff.lines().count();
        if (totalLines > MAX_TOTAL_DIFF_LINES) {
            skipAndBroadcast(aiReview, roomId, "총 diff 라인 수 초과 (" + totalLines + "줄)");
            return true;
        }
        return false;
    }

    private void skipAndBroadcast(AiReview aiReview, Long roomId, String reason) {
        log.info("AI 리뷰 스킵: {}", reason);
        aiReview.updateSkipped(reason);
        aiReviewRepository.save(aiReview);
        broadcastAiReviewStatus(roomId, aiReview);
    }

    private List<FileReviewResult> buildFileResults(PrReviewContext ctx, AiReview aiReview) {
        Map<String, String> fileDiffs = diffParser.parseFileDiffs(ctx.fullDiff());
        Map<String, String> fileStatuses = gitHubBotClient.getPrFileStatuses(
            ctx.repo().ownerName(), ctx.repo().repoName(), aiReview.getPrNumber());
        List<FileReviewResult> results = new ArrayList<>();

        for (Map.Entry<String, String> entry : fileDiffs.entrySet()) {
            String filePath = entry.getKey();
            String fileDiff = entry.getValue();

            if (fileDiff.lines().count() > MAX_FILE_DIFF_LINES) {
                log.info("파일 diff 라인 수 초과로 리뷰 스킵: {}", filePath);
                results.add(new FileReviewResult(filePath, null, null, List.of(), true));
                continue;
            }

            try {
                String status = fileStatuses.getOrDefault(filePath, "modified");
                if (!isReviewableFile(filePath)) {
                    log.info("리뷰 대상 아닌 파일 스킵: {}", filePath);
                    continue;
                }
                if ("deleted".equals(status)) {
                    log.info("삭제된 파일 스킵: {}", filePath);
                    continue;
                }

                String fileContent = gitHubBotClient.getFileContent(
                    ctx.repo().ownerName(), ctx.repo().repoName(), filePath, ctx.headSha());
                String baseContent = "added".equals(status) ? ""
                    : gitHubBotClient.getFileContent(
                        ctx.repo().ownerName(), ctx.repo().repoName(), filePath, ctx.baseSha());

                String context = ragContextService.buildContext(ctx.repoId(), filePath, fileDiff);
                if (!context.isBlank()) {
                    aiReview.markRagUsed();
                }
                String diffWithContext = context.isBlank()
                    ? fileDiff
                    : context + "\n\n위 컨텍스트를 참고해서 아래 PR을 리뷰해줘:\n\n" + fileDiff;

                List<InlineReview> reviews = geminiClient.reviewPrDiffInline(
                    diffWithContext, fileContent, aiReview.getPrTitle(), aiReview.getPrBody());
                results.add(new FileReviewResult(filePath, baseContent, fileContent, reviews, false));
            } catch (Exception e) {
                log.error("파일 리뷰 실패: {}", filePath, e);
            }
        }
        return results;
    }

    private void saveComments(AiReview aiReview, List<FileReviewResult> fileResults) {
        aiReviewCommentRepository.deleteByAiReview_Id(aiReview.getId());
        for (FileReviewResult file : fileResults) {
            if (file.reviews() == null || file.reviews().isEmpty()) continue;
            for (InlineReview review : file.reviews()) {
                AiReviewComment comment = AiReviewComment.builder()
                        .aiReview(aiReview)
                        .filePath(file.filePath())
                        .lineNumber(review.lineNumber())
                        .comment(review.comment())
                        .createdAt(LocalDateTime.now())
                        .build();
                aiReviewCommentRepository.save(comment);

                aiCommentModRepository.save(AiCommentMod.builder()
                        .comment(comment)
                        .active(true)
                        .changedBy("SYSTEM")
                        .createdAt(LocalDateTime.now())
                        .build());
            }
        }
    }

    @Transactional
    public void toggleComment(Long commentId, InactiveReason reason, String otherReason, String changedBy) {
        AiReviewComment comment = aiReviewCommentRepository.findById(commentId)
                .orElseThrow(() -> new AiReviewException(AiReviewErrorCode.COMMENT_NOT_FOUND));

        if (comment.getAiReview().isGithubPublished()) throw new AiReviewException(AiReviewErrorCode.ALREADY_PUBLISHED);

        AiCommentMod latest = aiCommentModRepository
                .findTopByComment_IdOrderByCreatedAtDesc(commentId)
                .orElseThrow(() -> new AiReviewException(AiReviewErrorCode.COMMENT_STATUS_NOT_FOUND));

        boolean newActive = !latest.isActive();
        validateToggleReason(newActive, reason, otherReason);

        aiCommentModRepository.save(AiCommentMod.builder()
                .comment(comment)
                .active(newActive)
                .reason(newActive ? null : reason)
                .otherReason(newActive ? null : otherReason)
                .changedBy(changedBy)
                .createdAt(LocalDateTime.now())
                .build());
    }

    private void validateToggleReason(boolean newActive, InactiveReason reason, String otherReason) {
        if (!newActive && reason == null) throw new AiReviewException(AiReviewErrorCode.INACTIVE_REASON_REQUIRED);
        if (!newActive && reason == InactiveReason.OTHER && (otherReason == null || otherReason.isBlank()))
            throw new AiReviewException(AiReviewErrorCode.OTHER_REASON_REQUIRED);
    }

    @Transactional(readOnly = true)
    public AiReviewResponse getAiReview(Long aiReviewId) {
        AiReview aiReview = aiReviewRepository.findById(aiReviewId)
                .orElseThrow(() -> new AiReviewException(AiReviewErrorCode.AI_REVIEW_NOT_FOUND));

        Map<Long, AiCommentMod> statusMap = aiCommentModRepository
                .findLatestStatusesByAiReviewId(aiReviewId).stream()
                .collect(Collectors.toMap(s -> s.getComment().getId(), s -> s));

        List<AiReviewComment> comments = aiReviewCommentRepository.findByAiReview_Id(aiReviewId);
        List<FileReviewResult> files = parseFileResults(aiReview);

        List<FileReviewResult> enriched = enrichWithCommentStatus(files, comments, statusMap);

        try {
            return new AiReviewResponse(
                    objectMapper.writeValueAsString(Map.of("files", enriched)),
                    aiReview.isGithubPublished(),
                    aiReview.getPublishedBy(),
                    aiReview.getPrTitle(),
                    aiReview.getPrBody()
            );
        } catch (Exception e) {
            throw new AiReviewException(AiReviewErrorCode.REVIEW_JSON_SERIALIZE_FAILED);
        }
    }

    private List<FileReviewResult> enrichWithCommentStatus(List<FileReviewResult> files,
                                                           List<AiReviewComment> comments,
                                                           Map<Long, AiCommentMod> statusMap) {
        // FileReviewResult가 record라 불변이므로 Map으로 응답 구성
        // getAiReview()의 응답은 별도 DTO로 분리하는 게 더 깔끔하나 일단 기존 구조 유지
        return files; // TODO: 응답 DTO 별도 분리 시 개선
    }

    @Transactional
    public AiReview createPendingAndMessage(ChatRoom room, int prNumber, String headSha,
                                            String botUsername, String prTitle, String prBody) {
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

        Member bot = memberService.getMemberByUsername(botUsername);
        chatRoomRedisRepository.genMessageSeq(room.getId());
        ChatMessage message = chatMessageMapper.toAiReviewMessageEntity(aiReview, bot, room);
        chatMessageRepository.save(message);

        messagingTemplate.convertAndSend("/topic/chat/" + room.getId(),
                chatMessageMapper.toAiReviewResponse(message));

        return aiReview;
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

    public boolean existsByRoomIdAndPrNumber(Long roomId, int prNumber) {
        return aiReviewRepository.existsByChatRoom_IdAndPrNumber(roomId, prNumber);
    }

    private AiReview findAiReview(Long roomId, int prNumber) {
        return aiReviewRepository.findByChatRoom_IdAndPrNumber(roomId, prNumber)
                .orElseThrow(() -> new AiReviewException(AiReviewErrorCode.AI_REVIEW_NOT_FOUND));
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
                            .publishedBy(aiReview.getPublishedBy())
                            .prStatus(aiReview.getPrStatus().name())
                            .senderId(message.getSender().getId())
                            .senderName(message.getSender().getNickname())
                            .profileImageUrl(message.getSender().getProfileImage())
                            .createdAt(message.getCreatedAt())
                            .build();
                    messagingTemplate.convertAndSend("/topic/chat/" + roomId, response);
                });
    }

    private List<FileReviewResult> parseFileResults(AiReview aiReview) {
        try {
            Map<String, Object> root = objectMapper.readValue(aiReview.getReviewJson(), new TypeReference<>() {});
            return objectMapper.convertValue(root.get("files"), new TypeReference<List<FileReviewResult>>() {});
        } catch (Exception e) {
            throw new AiReviewException(AiReviewErrorCode.REVIEW_JSON_PARSE_FAILED);
        }
    }

    private boolean isReviewableFile(String filePath) {
        return REVIEWABLE_EXTENSIONS.stream().anyMatch(filePath::endsWith);
    }
}