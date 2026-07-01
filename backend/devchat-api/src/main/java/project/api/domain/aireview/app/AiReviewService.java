package project.api.domain.aireview.app;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.api.domain.aireview.dao.AiCommentModRepository;
import project.api.domain.aireview.dao.AiReviewCommentRepository;
import project.api.domain.aireview.dao.AiReviewRepository;
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
import project.common.dto.FileReviewResult;
import project.common.dto.InlineReview;
import project.common.exception.errorcode.AiReviewErrorCode;
import project.common.exception.ex.AiReviewException;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiReviewService {

    private final ChatMessageRepository chatMessageRepository;
    private final AiReviewRepository aiReviewRepository;
    private final AiReviewCommentRepository aiReviewCommentRepository;
    private final AiCommentModRepository aiCommentModRepository;
    private final ChatMessageMapper chatMessageMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final MemberService memberService;
    private final ChatRoomRedisRepository chatRoomRedisRepository;
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

    @Transactional
    public void saveFileReviewResult(Long aiReviewId, Long chatRoomId, String filePath, List<InlineReview> reviews) {
        AiReview aiReview = findById(aiReviewId);

        for (InlineReview review : reviews) {
            AiReviewComment comment = AiReviewComment.builder()
                    .aiReview(aiReview)
                    .filePath(filePath)
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

        aiReview.incrementCompletedFiles();
        aiReviewRepository.save(aiReview);

        if (aiReview.isAllFilesCompleted()) {
            aiReview.updateSuccess();
            aiReviewRepository.save(aiReview);
            broadcastAiReviewStatus(chatRoomId, aiReview);
        }
    }

    @Transactional
    public void saveFileReviewFail(Long aiReviewId, Long chatRoomId, String filePath, String errorMessage) {
        AiReview aiReview = findById(aiReviewId);
        aiReview.incrementCompletedFiles();
        log.warn("파일 리뷰 실패: aiReviewId={}, filePath={}, error={}", aiReviewId, filePath, errorMessage);
        aiReviewRepository.save(aiReview);

        if (aiReview.isAllFilesCompleted()) {
            aiReview.updateSuccess();
            aiReviewRepository.save(aiReview);
            broadcastAiReviewStatus(chatRoomId, aiReview);
        }
    }

    @Transactional
    public Long resetToPendingAndPublish(ChatRoom room, int prNumber, String headSha, String prDiff) {
        AiReview aiReview = findAiReview(room.getId(), prNumber);
        aiCommentModRepository.deleteByComment_AiReview_Id(aiReview.getId());
        aiReviewCommentRepository.deleteByAiReview_Id(aiReview.getId());
        aiReview.resetToPending(headSha, prDiff);
        aiReviewRepository.save(aiReview);
        broadcastAiReviewStatus(room.getId(), aiReview);
        return aiReview.getId();
    }

    private AiReview findAiReview(Long roomId, int prNumber) {
        return aiReviewRepository.findByChatRoom_IdAndPrNumber(roomId, prNumber)
                .orElseThrow(() -> new AiReviewException(AiReviewErrorCode.AI_REVIEW_NOT_FOUND));
    }

    private void saveComments(AiReview aiReview, List<FileReviewResult> fileResults) {
        aiReviewCommentRepository.deleteByAiReview_Id(aiReview.getId());
        for (FileReviewResult file : fileResults) {
            if (file.reviews() == null || file.reviews().isEmpty()) continue;
            for (var review : file.reviews()) {
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

        List<FileReviewResult> files = parseFileResults(aiReview);

        try {
            return new AiReviewResponse(
                    objectMapper.writeValueAsString(Map.of("files", files)),
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

    public AiReview findById(Long aiReviewId) {
        return aiReviewRepository.findById(aiReviewId)
                .orElseThrow(() -> new AiReviewException(AiReviewErrorCode.AI_REVIEW_NOT_FOUND));
    }

    @Transactional
    public void updateTotalFiles(Long aiReviewId, int totalFiles) {
        AiReview aiReview = findById(aiReviewId);
        aiReview.updateTotalFiles(totalFiles);
        aiReviewRepository.save(aiReview);
    }
}