package project.backend.domain.chat.github.app;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import project.backend.domain.chat.chatmessage.dao.ChatMessageRepository;
import project.backend.domain.chat.chatmessage.dto.ChatMessageResponse;
import project.backend.domain.chat.chatmessage.entity.ChatMessage;
import project.backend.domain.chat.chatmessage.mapper.ChatMessageMapper;
import project.backend.domain.chat.chatroom.dao.ChatRoomRedisRepository;
import project.backend.domain.chat.chatroom.entity.ChatRoom;
import project.backend.domain.chat.github.GeminiClient;
import project.backend.domain.chat.github.GitHubBotClient;
import project.backend.domain.chat.github.GitRepoUrlUtils;
import project.backend.domain.chat.github.dto.GitRepoDto;
import project.backend.domain.member.app.MemberService;
import project.backend.domain.member.entity.Member;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiReviewService {

    private final GitHubBotClient gitHubBotClient;
    private final GeminiClient geminiClient;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatMessageMapper chatMessageMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final MemberService memberService;
    private final ChatRoomRedisRepository chatRoomRedisRepository;

    // PR 오픈 시 비동기로 AI 리뷰 자동 생성 → 채팅방에 전송
    @Async("chatBroadcastExecutor")
    public void triggerAiReview(ChatRoom room, int prNumber, String githubBotUsername) {
        try {
            GitRepoDto repo = GitRepoUrlUtils.validateAndParseUrl(room.getRepositoryUrl());

            log.info("AI 리뷰 시작: roomId={}, PR #{}", room.getId(), prNumber);

            // 1. diff 가져오기
            String diff = gitHubBotClient.getPrDiff(repo.ownerName(), repo.repoName(), prNumber);

            // 2. Gemini로 리뷰 생성
            String review = geminiClient.reviewPrDiff(diff);

            // 3. 채팅방에 AI 리뷰 메시지 전송 (GitHub 등록 버튼 포함)
            sendAiReviewMessage(room, review, prNumber, githubBotUsername);

        } catch (Exception e) {
            log.error("AI 리뷰 생성 실패: roomId={}, PR #{}", room.getId(), prNumber, e);
        }
    }

    private void sendAiReviewMessage(ChatRoom room, String review, int prNumber, String githubBotUsername) {
        Member githubBot = memberService.getMemberByUsername(githubBotUsername);
        chatRoomRedisRepository.genMessageSeq(room.getId());

        // AI 리뷰 메시지 저장 (prNumber 포함 - 나중에 GitHub 등록 시 사용)
        ChatMessage message = chatMessageMapper.toAiReviewEntity(review, prNumber, githubBot, room);
        chatMessageRepository.save(message);

        ChatMessageResponse response = chatMessageMapper.toAiReviewResponse(message);
        messagingTemplate.convertAndSend("/topic/chat/" + room.getId(), response);

        log.info("AI 리뷰 채팅방 전송 완료: roomId={}, PR #{}", room.getId(), prNumber);
    }

    // 확인 버튼 클릭 시 GitHub에 등록
    public void publishToGitHub(ChatRoom room, Long messageId) {
        ChatMessage message = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("메시지를 찾을 수 없습니다."));

        if (message.isGithubPublished()) {
            throw new IllegalStateException("이미 GitHub에 등록된 리뷰입니다.");
        }

        GitRepoDto repo = GitRepoUrlUtils.validateAndParseUrl(room.getRepositoryUrl());

        int prNumber = parsePrNumber(message.getContent());
        String reviewBody = message.getContent()
                .substring(message.getContent().indexOf("\n") + 1);

        gitHubBotClient.postReviewComment(
                repo.ownerName(),
                repo.repoName(),
                prNumber,
                reviewBody
        );

        message.markAsPublished();
        chatMessageRepository.save(message);

        log.info("GitHub PR 리뷰 등록 완료: roomId={}, PR #{}", room.getId(), prNumber);
    }

    private int parsePrNumber(String content) {
        String firstLine = content.split("\n")[0];
        return Integer.parseInt(firstLine.replace("PR_NUMBER:", "").trim());
    }
}