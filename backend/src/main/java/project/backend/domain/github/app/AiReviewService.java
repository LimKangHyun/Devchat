package project.backend.domain.github.app;

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
import project.backend.domain.github.dao.AiReviewRepository;
import project.backend.domain.github.dto.GitRepoDto;
import project.backend.domain.github.entity.AiReview;
import project.backend.domain.github.entity.AiReviewStatus;
import project.backend.domain.member.app.MemberService;
import project.backend.domain.member.entity.Member;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private final ObjectMapper objectMapper;

    @Async("chatBroadcastExecutor")
    public void triggerAiReview(ChatRoom room, int prNumber, String headSha, String baseSha) {
        AiReview aiReview = aiReviewRepository
                .findByChatRoom_IdAndPrNumber(room.getId(), prNumber)
                .orElseThrow(() -> new IllegalStateException("AI 리뷰를 찾을 수 없습니다."));

        if (aiReview.getStatus() == AiReviewStatus.FAIL) {
            aiReview.resetToPending(headSha);
            aiReviewRepository.save(aiReview);
        }

        broadcastAiReviewStatus(room.getId(), aiReview);

        try {
            GitRepoDto repo = GitRepoUrlUtils.validateAndParseUrl(room.getRepositoryUrl());
            String fullDiff = gitHubBotClient.getPrDiff(repo.ownerName(), repo.repoName(), prNumber);
            List<Map<String, Object>> fileResults = buildFileResults(repo, fullDiff, headSha, baseSha);

            if (fileResults.isEmpty()) {
                aiReview.updateFail("모든 파일 리뷰 생성 실패");
            } else {
                aiReview.updateSuccess(objectMapper.writeValueAsString(Map.of("files", fileResults)));
            }
        } catch (Exception e) {
            log.error("AI 리뷰 생성 실패: roomId={}, PR #{}", room.getId(), prNumber, e);
            aiReview.updateFail(e.getMessage());
        }

        aiReviewRepository.save(aiReview);
        broadcastAiReviewStatus(room.getId(), aiReview);
    }

    private List<Map<String, Object>> buildFileResults(GitRepoDto repo, String fullDiff, String headSha, String baseSha) {
        Map<String, String> fileDiffs = parseFileDiffs(fullDiff);
        List<Map<String, Object>> fileResults = new ArrayList<>();

        for (Map.Entry<String, String> entry : fileDiffs.entrySet()) {
            String filePath = entry.getKey();
            try {
                String fileContent = gitHubBotClient.getFileContent(
                        repo.ownerName(), repo.repoName(), filePath, headSha);
                String baseContent = gitHubBotClient.getFileContent(
                        repo.ownerName(), repo.repoName(), filePath, baseSha);
                List<Map<String, Object>> reviews = geminiClient.reviewPrDiffInline(entry.getValue(), fileContent);
                fileResults.add(Map.of("filePath", filePath, "baseContent", baseContent, "fileContent", fileContent, "reviews", reviews));
            } catch (Exception e) {
                log.error("파일 리뷰 실패: {}", filePath, e);
            }
        }
        return fileResults;
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
                            .build();
                    messagingTemplate.convertAndSend("/topic/chat/" + roomId, response);
                });
    }

    // PR 오픈 시 채팅방에 메시지 1개만 생성 (triggerAiReview와 분리)
    @Transactional
    public AiReview createPendingAndMessage(ChatRoom room, int prNumber, String headSha, String githubBotUsername) {
        AiReview aiReview = AiReview.builder()
                .chatRoom(room)
                .prNumber(prNumber)
                .commitSha(headSha)
                .status(AiReviewStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        aiReviewRepository.save(aiReview);

        // 채팅 메시지 1개 생성
        Member githubBot = memberService.getMemberByUsername(githubBotUsername);
        chatRoomRedisRepository.genMessageSeq(room.getId());
        ChatMessage message = chatMessageMapper.toAiReviewMessageEntity(aiReview, githubBot, room);
        chatMessageRepository.save(message);
        log.info("저장된 message aiReviewId: {}", message.getAiReview() != null ? message.getAiReview().getId() : null);

        ChatMessageResponse response = chatMessageMapper.toAiReviewResponse(message);
        messagingTemplate.convertAndSend("/topic/chat/" + room.getId(), response);

        return aiReview;
    }

    public void publishToGitHub(ChatRoom room, Long aiReviewId) {
        AiReview aiReview = aiReviewRepository.findById(aiReviewId)
                .orElseThrow(() -> new IllegalArgumentException("AI 리뷰를 찾을 수 없습니다."));

        GitRepoDto repo = GitRepoUrlUtils.validateAndParseUrl(room.getRepositoryUrl());
        gitHubBotClient.postReviewComment(
                repo.ownerName(), repo.repoName(),
                aiReview.getPrNumber(), aiReview.getReviewJson()
        );

        log.info("GitHub PR 리뷰 등록 완료: roomId={}, PR #{}", room.getId(), aiReview.getPrNumber());
    }

    private Map<String, String> parseFileDiffs(String fullDiff) {
        Map<String, String> fileDiffs = new LinkedHashMap<>();
        Pattern pattern = Pattern.compile(
                "(diff --git a/.+ b/(.+?)\\n.*?)(?=diff --git |$)", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(fullDiff);
        while (matcher.find()) {
            fileDiffs.put(matcher.group(2), matcher.group(1));
        }
        return fileDiffs;
    }
}