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

import java.util.ArrayList;
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
    private final ChatMessageMapper chatMessageMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final MemberService memberService;
    private final ChatRoomRedisRepository chatRoomRedisRepository;

    @Async("chatBroadcastExecutor")
    public void triggerAiReview(ChatRoom room, int prNumber, String githubBotUsername, String headSha) {
        try {
            GitRepoDto repo = GitRepoUrlUtils.validateAndParseUrl(room.getRepositoryUrl());
            log.info("AI 리뷰 시작: roomId={}, PR #{}", room.getId(), prNumber);

            // 1. diff 가져오기
            String diff = gitHubBotClient.getPrDiff(repo.ownerName(), repo.repoName(), prNumber);

            // 2. 변경된 파일 목록 파싱
            List<String> filePaths = parseFilePaths(diff);
            log.info("변경된 파일 목록: {}", filePaths);

            // 3. 파일별로 전체 코드 가져와서 인라인 리뷰 생성
            for (String filePath : filePaths) {
                String fileContent = gitHubBotClient.getFileContent(
                        repo.ownerName(), repo.repoName(), filePath, headSha);

                List<Map<String, Object>> inlineReviews =
                        geminiClient.reviewPrDiffInline(diff, fileContent);

                log.info("인라인 리뷰 생성 결과: {}", inlineReviews);

                // 4. 채팅방에 AI 리뷰 메시지 전송
                sendAiReviewMessage(room, fileContent, filePath, prNumber, inlineReviews, githubBotUsername);
            }

        } catch (Exception e) {
            log.error("AI 리뷰 생성 실패: roomId={}, PR #{}", room.getId(), prNumber, e);
        }
    }

    private void sendAiReviewMessage(ChatRoom room, String fileContent, String filePath,
                                     int prNumber, List<Map<String, Object>> inlineReviews,
                                     String githubBotUsername) {
        Member githubBot = memberService.getMemberByUsername(githubBotUsername);
        chatRoomRedisRepository.genMessageSeq(room.getId());

        ChatMessage message = chatMessageMapper.toAiReviewEntity(
                fileContent, filePath, prNumber, inlineReviews, githubBot, room);
        chatMessageRepository.save(message);

        ChatMessageResponse response = chatMessageMapper.toAiReviewResponse(message);
        messagingTemplate.convertAndSend("/topic/chat/" + room.getId(), response);

        log.info("AI 리뷰 채팅방 전송 완료: roomId={}, PR #{}, 파일={}", room.getId(), prNumber, filePath);
    }

    private List<String> parseFilePaths(String diff) {
        List<String> paths = new ArrayList<>();
        Pattern pattern = Pattern.compile("^diff --git a/.+ b/(.+)$", Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(diff);
        while (matcher.find()) {
            paths.add(matcher.group(1));
        }
        return paths;
    }

    public void publishToGitHub(ChatRoom room, Long messageId) {
        ChatMessage message = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("메시지를 찾을 수 없습니다."));

        if (message.isGithubPublished()) {
            throw new IllegalStateException("이미 GitHub에 등록된 리뷰입니다.");
        }

        GitRepoDto repo = GitRepoUrlUtils.validateAndParseUrl(room.getRepositoryUrl());

        int prNumber = message.getPrNumber();
        String reviewBody = message.getInlineReviews();

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
}