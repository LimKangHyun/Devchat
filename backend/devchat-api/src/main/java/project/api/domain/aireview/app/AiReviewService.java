package project.api.domain.aireview.app;

import project.common.message.aireview.FileClaimResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.api.domain.aireview.dao.AiCommentModRepository;
import project.api.domain.aireview.dao.AiReviewCommentRepository;
import project.api.domain.aireview.dao.AiReviewFileRepository;
import project.api.domain.aireview.dao.AiReviewRepository;
import project.api.domain.aireview.dto.AiReviewCommentResponse;
import project.api.domain.aireview.dto.AiReviewResponse;
import project.api.domain.aireview.entity.*;
import project.api.domain.chat.chatmessage.dao.ChatMessageRepository;
import project.api.domain.chat.chatmessage.dto.ChatMessageResponse;
import project.api.domain.chat.chatmessage.entity.ChatMessage;
import project.api.domain.chat.chatmessage.entity.MessageType;
import project.api.domain.chat.chatmessage.mapper.ChatMessageMapper;
import project.api.domain.chat.chatroom.dao.ChatRoomRedisRepository;
import project.api.domain.chat.chatroom.entity.ChatRoom;
import project.api.domain.member.app.MemberService;
import project.api.domain.member.entity.Member;
import project.common.dto.InlineReview;
import project.common.exception.errorcode.AiReviewErrorCode;
import project.common.exception.ex.AiReviewException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiReviewService {

    private final ChatMessageRepository chatMessageRepository;
    private final AiReviewRepository aiReviewRepository;
    private final AiReviewCommentRepository aiReviewCommentRepository;
    private final AiCommentModRepository aiCommentModRepository;
    private final AiReviewFileRepository aiReviewFileRepository;
    private final ChatMessageMapper chatMessageMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final MemberService memberService;
    private final ChatRoomRedisRepository chatRoomRedisRepository;
    private final AiReviewDiffParser diffParser;
    private final ObjectMapper objectMapper;

    @Transactional
    public Long createPendingAndMessage(ChatRoom room, int prNumber, String headSha,
                                        String botUsername, String prTitle, String prBody, String prDiff) {
        AiReview aiReview = AiReview.builder()
                .chatRoom(room)
                .prNumber(prNumber)
                .commitSha(headSha)
                .status(AiReviewStatus.PENDING)
                .prTitle(prTitle)
                .prBody(prBody)
                .prDiff(prDiff)
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

        return aiReview.getId();
    }

    /**
     * AI 서버가 파일 리뷰를 시작하기 전에 처리 권한을 얻는다. 같은 파일을 두 Worker가 동시에 리뷰하지 않게 한다.
     *
     * @param reclaim 재처리 스케줄러가 앞 Worker의 Heartbeat 만료를 확인하고 다시 보낸 요청이면 true.
     *                이때는 PROCESSING으로 남은 파일의 권한을 넘겨받는다.
     */
    @Transactional
    public FileClaimResult claimFile(Long aiReviewId, String filePath, String commitSha, boolean reclaim) {
        AiReview aiReview = aiReviewRepository.findById(aiReviewId).orElse(null);
        if (aiReview == null || isStaleCommit(aiReview, commitSha)) {
            return FileClaimResult.STALE_COMMIT;
        }
        LocalDateTime now = LocalDateTime.now();
        if (aiReviewFileRepository.insertIfAbsent(aiReviewId, filePath, FileReviewStatus.PROCESSING.name(), now) == 1) {
            return FileClaimResult.CLAIMED;
        }
        if (reclaim && aiReviewFileRepository.takeOverProcessing(aiReviewId, filePath, now) == 1) {
            log.warn("앞 Worker가 멈춘 파일의 처리 권한을 넘겨받음 - aiReviewId={}, filePath={}", aiReviewId, filePath);
            return FileClaimResult.CLAIMED;
        }
        return FileClaimResult.ALREADY_TAKEN;
    }

    /**
     * @param commitSha 결과를 만든 요청의 커밋. 그사이 새 커밋으로 리뷰가 다시 시작됐으면 이 결과를 버린다.
     */
    @Transactional
    public void saveFileReviewResult(Long aiReviewId, Long chatRoomId, String filePath, String commitSha,
                                     List<InlineReview> reviews) {
        saveFileReviewResult(aiReviewId, chatRoomId, filePath, commitSha, null, reviews);
    }

    /**
     * @param promptVersion 코멘트를 만든 프롬프트 버전. 프롬프트별 채택률 비교(리뷰 품질 대시보드)에 쓴다. 이전 AI 서버는 null
     */
    @Transactional
    public void saveFileReviewResult(Long aiReviewId, Long chatRoomId, String filePath, String commitSha,
                                     String promptVersion, List<InlineReview> reviews) {
        // 이 파일의 최종 결과를 처음 반영하는 경우만 통과한다. 중복이면 코멘트 저장과 카운터 증가를 모두 건너뛴다.
        // (상태 변경 쿼리가 영속성 컨텍스트를 비우므로, 리뷰 엔티티는 그 뒤에 읽는다)
        if (!markFileFinished(aiReviewId, filePath, commitSha, FileReviewStatus.COMPLETED)) {
            return;
        }
        AiReview aiReview = findById(aiReviewId);

        for (InlineReview review : reviews) {
            AiReviewComment comment = AiReviewComment.builder()
                    .aiReview(aiReview)
                    .filePath(filePath)
                    .lineNumber(review.lineNumber())
                    .diffLine(review.diffLine())
                    .comment(review.comment())
                    .createdAt(LocalDateTime.now())
                    .promptVersion(promptVersion)
                    .build();
            aiReviewCommentRepository.save(comment);

            aiCommentModRepository.save(AiCommentMod.builder()
                    .comment(comment)
                    .active(true)
                    .changedBy("SYSTEM")
                    .createdAt(LocalDateTime.now())
                    .build());
        }

        aiReview.incrementCompletedFiles();
        aiReviewRepository.save(aiReview);

        completeIfAllFilesDone(chatRoomId, aiReview);
    }

    @Transactional
    public void saveFileReviewFail(Long aiReviewId, Long chatRoomId, String filePath, String commitSha,
                                   String errorMessage) {
        if (!markFileFinished(aiReviewId, filePath, commitSha, FileReviewStatus.FAILED)) {
            return;
        }
        AiReview aiReview = findById(aiReviewId);

        aiReview.incrementCompletedFiles();
        aiReview.incrementFailedFiles();
        log.warn("파일 리뷰 실패: aiReviewId={}, filePath={}, error={}", aiReviewId, filePath, errorMessage);
        aiReviewRepository.save(aiReview);

        completeIfAllFilesDone(chatRoomId, aiReview);
    }

    /**
     * AI 서버가 리뷰를 건너뛴 파일 (예: 채팅방의 하루 LLM 사용량 한도 초과).
     * 실패로 세지 않고 건너뛴 파일 목록에 사유를 남긴다. 파일 상태는 FAILED로 둬서 다음 커밋의 증분 리뷰에서 다시 리뷰된다.
     */
    @Transactional
    public void saveFileReviewSkipped(Long aiReviewId, Long chatRoomId, String filePath, String commitSha,
                                      String reason) {
        if (!markFileFinished(aiReviewId, filePath, commitSha, FileReviewStatus.FAILED)) {
            return;
        }
        addSkippedFile(aiReviewId, filePath, reason);
        AiReview aiReview = findById(aiReviewId);
        aiReview.incrementCompletedFiles();
        log.info("파일 리뷰 건너뜀: aiReviewId={}, filePath={}, reason={}", aiReviewId, filePath, reason);
        aiReviewRepository.save(aiReview);

        completeIfAllFilesDone(chatRoomId, aiReview);
    }

    /**
     * 파일의 최종 결과를 반영해도 되는지 판단하고, 되면 상태를 바꾼다. 처음 반영하는 경우만 true.
     * - 예전 커밋의 결과: 버린다.
     * - PROCESSING → 최종 상태: 조건부 UPDATE라 두 Worker가 결과를 보내도 한 번만 성공한다(상태 기반 멱등 처리).
     * - 행이 없음: claim 없이 처리한 이전 버전 AI 서버의 결과(배포 중 섞이는 경우)라 새로 넣는다. 이미 있으면 중복이다.
     *
     * 예전에는 행을 새로 넣으면서 UNIQUE 위반 예외로 중복을 판별했는데, JPA는 이 예외가 나면 트랜잭션을
     * 롤백 전용으로 바꿔서 중복 메시지가 오면 커밋에서 예외가 났다. 지금은 예외 없이 영향받은 행 수로 판단한다.
     */
    private boolean markFileFinished(Long aiReviewId, String filePath, String commitSha, FileReviewStatus status) {
        AiReview aiReview = aiReviewRepository.findById(aiReviewId).orElse(null);
        if (aiReview == null) return false;
        if (isStaleCommit(aiReview, commitSha)) {
            log.info("예전 커밋의 리뷰 결과, 버림 - aiReviewId={}, filePath={}, resultCommit={}, currentCommit={}",
                    aiReviewId, filePath, commitSha, aiReview.getCommitSha());
            return false;
        }
        if (aiReviewFileRepository.updateStatusIfProcessing(aiReviewId, filePath, status) == 1) {
            return true;
        }
        if (aiReviewFileRepository.insertIfAbsent(aiReviewId, filePath, status.name(), LocalDateTime.now()) == 1) {
            return true;
        }
        log.info("이미 처리된 파일, 중복 결과 스킵 - aiReviewId={}, filePath={}", aiReviewId, filePath);
        return false;
    }

    /** 커밋 정보가 없는 이전 형식 메시지는 검사하지 않는다. */
    private static boolean isStaleCommit(AiReview aiReview, String commitSha) {
        return commitSha != null && !commitSha.equals(aiReview.getCommitSha());
    }

    private void completeIfAllFilesDone(Long chatRoomId, AiReview aiReview) {
        if (!aiReview.isAllFilesCompleted()) return;

        AiReviewStatus finalStatus = aiReview.isAllFilesFailed()
                ? AiReviewStatus.FAIL
                : AiReviewStatus.SUCCESS;

        int updated = aiReviewRepository.markFinalStatusIfPending(
                aiReview.getId(), finalStatus, LocalDateTime.now());

        if (updated == 0) {
            log.info("이미 다른 인스턴스가 완료 처리함, 알림 스킵 - aiReviewId={}", aiReview.getId());
            return;
        }

        if (finalStatus == AiReviewStatus.FAIL) {
            aiReview.updateFail("모든 파일 리뷰 실패");
        } else {
            aiReview.updateSuccess();
        }
        broadcastAiReviewStatus(chatRoomId, aiReview);
    }

    @Transactional
    public Long resetToPendingAndPublish(ChatRoom room, int prNumber, String headSha, String prDiff) {
        return resetForNewCommit(room, prNumber, headSha, prDiff, null).aiReviewId();
    }

    /**
     * 증분 리뷰에 쓸 "마지막으로 리뷰를 끝낸 커밋". 리뷰가 끝난(SUCCESS/FAIL) 상태일 때만 돌려준다.
     * 리뷰가 아직 진행 중이거나 건너뛴(SKIPPED) 상태면 재사용할 결과가 없으므로 null.
     */
    @Transactional(readOnly = true)
    public String findReviewedCommitSha(Long roomId, int prNumber) {
        return aiReviewRepository.findByChatRoom_IdAndPrNumber(roomId, prNumber)
                .filter(r -> r.getStatus() == AiReviewStatus.SUCCESS || r.getStatus() == AiReviewStatus.FAIL)
                .map(AiReview::getCommitSha)
                .orElse(null);
    }

    /** @param reusedFiles 이전 리뷰 결과를 그대로 쓰는 파일. 비어 있으면 전체 리뷰 */
    public record ResetResult(Long aiReviewId, Set<String> reusedFiles) {}

    /**
     * 새 커밋으로 리뷰를 다시 시작한다.
     *
     * @param changedSinceReviewed 마지막으로 리뷰한 커밋 이후 바뀐 파일. null이면 전체를 다시 리뷰한다.
     *                             값이 있으면 이전 리뷰에서 끝낸(COMPLETED) 파일 중 바뀌지 않은 파일의 코멘트를 남기고 재사용한다.
     */
    @Transactional
    public ResetResult resetForNewCommit(ChatRoom room, int prNumber, String headSha, String prDiff,
                                         Set<String> changedSinceReviewed) {
        AiReview aiReview = findAiReview(room.getId(), prNumber);

        Set<String> reused = new HashSet<>();
        if (changedSinceReviewed != null) {
            reused.addAll(aiReviewFileRepository.findFilePathsByStatus(aiReview.getId(), FileReviewStatus.COMPLETED));
            reused.removeAll(changedSinceReviewed);
            // 지금 PR diff에 없는 파일(되돌린 파일 등)은 재사용하지 않는다
            reused.retainAll(diffParser.parseFileDiffs(prDiff).keySet());
        }

        if (reused.isEmpty()) {
            aiCommentModRepository.deleteByComment_AiReview_Id(aiReview.getId());
            aiReviewCommentRepository.deleteByAiReview_Id(aiReview.getId());
            aiReviewFileRepository.deleteByAiReview_Id(aiReview.getId());
        } else {
            aiCommentModRepository.deleteByAiReviewIdExceptFiles(aiReview.getId(), reused);
            aiReviewCommentRepository.deleteByAiReviewIdExceptFiles(aiReview.getId(), reused);
            aiReviewFileRepository.deleteByAiReviewIdExceptFiles(aiReview.getId(), reused);
        }

        aiReview.resetToPending(headSha, prDiff);
        aiReviewRepository.save(aiReview);
        broadcastAiReviewStatus(room.getId(), aiReview);
        return new ResetResult(aiReview.getId(), Set.copyOf(reused));
    }

    /**
     * 새로 리뷰할 파일이 없을 때 바로 끝낸다. 이전 결과를 재사용한 파일이 있으면 SUCCESS, 없으면 SKIPPED.
     * (그대로 두면 totalFiles가 0이라 완료 판정이 영영 일어나지 않는다)
     */
    @Transactional
    public void completeWithoutNewFiles(Long aiReviewId, boolean hasReusedFiles) {
        AiReview aiReview = findByIdWithChatRoom(aiReviewId);
        if (!hasReusedFiles) {
            updateSkipped(aiReview, "리뷰할 파일 없음");
            return;
        }
        int updated = aiReviewRepository.markFinalStatusIfPending(aiReviewId, AiReviewStatus.SUCCESS, LocalDateTime.now());
        if (updated > 0) {
            broadcastAiReviewStatus(aiReview.getChatRoom().getId(), findByIdWithChatRoom(aiReviewId));
        }
    }

    /**
     * 아직 PENDING인 리뷰를 실패로 끝낸다 (리뷰 요청을 끝내 만들지 못했을 때).
     * 이미 다른 상태로 끝났거나 새 커밋으로 다시 시작해 다른 결과가 났으면 건드리지 않는다.
     */
    @Transactional
    public void failPendingReview(Long aiReviewId, String reason) {
        int updated = aiReviewRepository.markFinalStatusIfPending(aiReviewId, AiReviewStatus.FAIL, LocalDateTime.now());
        if (updated == 0) return;
        AiReview aiReview = findByIdWithChatRoom(aiReviewId);
        aiReview.updateFail(reason);
        aiReviewRepository.save(aiReview);
        log.warn("AI 리뷰 실패 처리 - aiReviewId={}, reason={}", aiReviewId, reason);
        broadcastAiReviewStatus(aiReview.getChatRoom().getId(), aiReview);
    }

    private AiReview findAiReview(Long roomId, int prNumber) {
        return aiReviewRepository.findByChatRoom_IdAndPrNumber(roomId, prNumber)
                .orElseThrow(() -> new AiReviewException(AiReviewErrorCode.AI_REVIEW_NOT_FOUND));
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

        List<AiReviewComment> comments = aiReviewCommentRepository.findByAiReview_Id(aiReviewId);

        Map<String, List<AiReviewCommentResponse>> filesGrouped = comments.stream()
                .map(this::toCommentResponse)
                .collect(Collectors.groupingBy(AiReviewCommentResponse::filePath));

        Map<String, String> fileContents = parseJsonMap(aiReview.getFileContents());
        Map<String, String> beforeFileContents =
                diffParser.parseBeforeContents(aiReview.getPrDiff(), fileContents);
        Map<String, String> skippedFiles = parseJsonMap(aiReview.getSkippedFiles());

        return new AiReviewResponse(
                filesGrouped,
                fileContents,
                beforeFileContents,
                skippedFiles,
                aiReview.isGithubPublished(),
                aiReview.getPublishedBy(),
                aiReview.getPrTitle(),
                aiReview.getPrBody()
        );
    }

    private Map<String, String> parseJsonMap(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            log.error("JSON 파싱 실패: {}", json, e);
            return Map.of();
        }
    }

    private AiReviewCommentResponse toCommentResponse(AiReviewComment comment) {
        boolean active = aiCommentModRepository
                .findTopByComment_IdOrderByCreatedAtDesc(comment.getId())
                .map(AiCommentMod::isActive)
                .orElse(true);

        return new AiReviewCommentResponse(
                comment.getId(),
                comment.getFilePath(),
                comment.getLineNumber(),
                comment.getDiffLine(),
                comment.getComment(),
                active
        );
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

    public boolean existsByRoomIdAndPrNumber(Long roomId, int prNumber) {
        return aiReviewRepository.existsByChatRoom_IdAndPrNumber(roomId, prNumber);
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

    public AiReview findById(Long aiReviewId) {
        return aiReviewRepository.findById(aiReviewId)
                .orElseThrow(() -> new AiReviewException(AiReviewErrorCode.AI_REVIEW_NOT_FOUND));
    }

    public AiReview findByIdWithChatRoom(Long aiReviewId) {
        return aiReviewRepository.findByIdWithChatRoom(aiReviewId)
                .orElseThrow(() -> new AiReviewException(AiReviewErrorCode.AI_REVIEW_NOT_FOUND));
    }

    @Transactional
    public void updateTotalFiles(Long aiReviewId, int totalFiles) {
        AiReview aiReview = findById(aiReviewId);
        aiReview.updateTotalFiles(totalFiles);
        aiReviewRepository.save(aiReview);
    }

    @Transactional
    public void saveFileContents(Long aiReviewId, Map<String, String> fileContentMap) {
        saveFileContents(aiReviewId, fileContentMap, Set.of());
    }

    /** @param keepExisting 기존 코드를 그대로 둘 파일(증분 리뷰에서 재사용한 파일). 화면에서 코멘트와 함께 코드를 보여 주는 데 쓴다 */
    @Transactional
    public void saveFileContents(Long aiReviewId, Map<String, String> fileContentMap, Set<String> keepExisting) {
        AiReview aiReview = findById(aiReviewId);
        try {
            Map<String, String> merged = new HashMap<>();
            if (!keepExisting.isEmpty() && aiReview.getFileContents() != null) {
                Map<String, String> existing = objectMapper.readValue(aiReview.getFileContents(),
                        new TypeReference<Map<String, String>>() {});
                existing.forEach((path, code) -> {
                    if (keepExisting.contains(path)) merged.put(path, code);
                });
            }
            merged.putAll(fileContentMap);
            aiReview.saveFileContents(objectMapper.writeValueAsString(merged));
            aiReviewRepository.save(aiReview);
        } catch (Exception e) {
            log.error("fileContents 저장 실패. aiReviewId={}", aiReviewId, e);
        }
    }

    @Transactional
    public void updateSkipped(AiReview aiReview, String reason) {
        aiReview.updateSkipped(reason);
        aiReviewRepository.save(aiReview);
        broadcastAiReviewStatus(aiReview.getChatRoom().getId(), aiReview);
    }

    @Transactional
    public void addSkippedFile(Long aiReviewId, String filePath, String reason) {
        AiReview aiReview = findById(aiReviewId);
        try {
            Map<String, String> skippedMap = new HashMap<>();
            if (aiReview.getSkippedFiles() != null) {
                skippedMap = objectMapper.readValue(aiReview.getSkippedFiles(),
                        new TypeReference<Map<String, String>>() {});
            }
            skippedMap.put(filePath, reason);
            aiReview.updateSkippedFiles(objectMapper.writeValueAsString(skippedMap));
            aiReviewRepository.save(aiReview);
        } catch (Exception e) {
            log.error("skippedFiles 저장 실패. aiReviewId={}, filePath={}", aiReviewId, filePath, e);
        }
    }
}