package project.backend.domain.chat.github.app;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.backend.auth.app.AuthTokenService;
import project.backend.domain.chat.chatmessage.dao.ChatMessageRepository;
import project.backend.domain.chat.chatmessage.dto.ChatMessageResponse;
import project.backend.domain.chat.chatmessage.entity.ChatMessage;
import project.backend.domain.chat.chatmessage.mapper.ChatMessageMapper;
import project.backend.domain.chat.chatroom.dao.ChatRoomRedisRepository;
import project.backend.domain.chat.chatroom.dao.ChatRoomRepository;
import project.backend.domain.chat.chatroom.entity.ChatRoom;
import project.backend.domain.chat.github.GeminiClient;
import project.backend.domain.chat.github.GitHubUserClient;
import project.backend.domain.chat.github.GitRepoUrlUtils;
import project.backend.domain.chat.github.dto.GitMessageDto;
import project.backend.domain.chat.github.dto.GitRepoDto;
import project.backend.domain.member.app.MemberService;
import project.backend.domain.member.entity.Member;
import project.backend.global.exception.errorcode.ChatRoomErrorCode;
import project.backend.global.exception.ex.ChatRoomException;

@Service
@Slf4j
@RequiredArgsConstructor
public class GitMessageService {

	private final ChatRoomRepository chatRoomRepository;
	private final ChatMessageMapper chatMessageMapper;
	private final ChatMessageRepository chatMessageRepository;
	private final SimpMessagingTemplate messagingTemplate;
	private final MemberService memberService;
	private final GitHubUserClient gitHubUserClient;
	private final AuthTokenService authTokenService;
	private final ChatRoomRedisRepository chatRoomRedisRepository;
	private final AiReviewService aiReviewService;
	private final GeminiClient geminiClient;

	@Value("${url.webhook-url}")
	private String webhookUrl;

	@Value("${github.username}")
	private String githubUsername;

	@Transactional
	public void handleEvent(Long roomId, String eventType, Map<String, Object> payload) {
		GitMessageDto gitMessage = switch (eventType) {
			case "issues" -> GitMessageDto.fromIssue(payload);
			case "pull_request" -> GitMessageDto.fromPullRequest(payload);
			case "pull_request_review" -> GitMessageDto.fromPullRequestReview(payload);
			case "workflow_run" -> GitMessageDto.fromWorkflowRun(payload);
			default -> null;
		};

		if (gitMessage == null) return;

		ChatRoom room = chatRoomRepository.findById(roomId)
				.orElseThrow(() -> new ChatRoomException(ChatRoomErrorCode.CHATROOM_NOT_FOUND));

		sendGitMessage(room, gitMessage.attachRoom(gitMessage, room));

		// PR 오픈 시 AI 리뷰 자동 트리거
		if ("pull_request".equals(eventType) && "opened".equals(payload.get("action"))) {
			log.info("PR payload: {}", payload);
			Map<String, Object> pr = (Map<String, Object>) payload.get("pull_request");
			int prNumber = (int) pr.get("number");
			Map<String, Object> head = (Map<String, Object>) pr.get("head");
			String headSha = (String) head.get("sha");
			aiReviewService.triggerAiReview(room, prNumber, githubUsername, headSha);
		}
	}

	private void sendGitMessage(ChatRoom room, GitMessageDto gitMessage) {
		Member githubBot = memberService.getMemberByUsername(githubUsername);
		chatRoomRedisRepository.genMessageSeq(room.getId());

		// AI 요약 생성
		String summarized = geminiClient.summarizeGitEvent(
				gitMessage.getFullContent() != null ? gitMessage.getFullContent() : gitMessage.getContent(),
				gitMessage.getType()
		);

		// 원본 + 요약 합치기
		String combined = gitMessage.getContent() + "\n\n" + summarized;
		gitMessage.updateContent(combined);

		ChatMessage message = chatMessageMapper.toEntityWithGit(gitMessage, githubBot);
		chatMessageRepository.save(message);
		ChatMessageResponse response = chatMessageMapper.toGitResponse(message);
		messagingTemplate.convertAndSend("/topic/chat/" + room.getId(), response);
	}

	public void registerWebhook(String repoUrl, Long roomId, Long memberId) {
		GitRepoDto gitRepoDto = GitRepoUrlUtils.validateAndParseUrl(repoUrl);

		log.info("owner = {}", gitRepoDto.ownerName());
		log.info("repo = {}", gitRepoDto.repoName());

		String githubAccessToken = authTokenService.getGithubAccessToken(memberId);

		String webhookUrl = makeWebhookUrl(roomId);

		gitHubUserClient.validateAdminPermission(githubAccessToken,
				gitRepoDto.ownerName(), gitRepoDto.repoName());

		Long webhookId = gitHubUserClient.registerWebhook(githubAccessToken,
				gitRepoDto.ownerName(), gitRepoDto.repoName(), webhookUrl);

		ChatRoom room = chatRoomRepository.findById(roomId)
				.orElseThrow(() -> new ChatRoomException(ChatRoomErrorCode.CHATROOM_NOT_FOUND));

		room.updateWebhookId(webhookId);
	}

	private String makeWebhookUrl(Long roomId) {
		return webhookUrl + "/github/" + roomId;
	}

	public void deleteWebhook(ChatRoom room, Long ownerId) {
		if (room.getRepositoryUrl() == null || room.getWebhookId() == null) {
			log.info("GitHub 연동이 없는 채팅방입니다. 웹훅 삭제 스킵: roomId={}", room.getId());
			return;
		}

		GitRepoDto gitRepoDto = GitRepoUrlUtils.validateAndParseUrl(room.getRepositoryUrl());

		String githubAccessToken = authTokenService.getGithubAccessToken(ownerId);

		gitHubUserClient.deleteWebhook(githubAccessToken,
				gitRepoDto.ownerName(), gitRepoDto.repoName(), room.getWebhookId());
	}

	public void publishAiReview(Long roomId, Long messageId) {
		ChatRoom room = chatRoomRepository.findById(roomId)
				.orElseThrow(() -> new ChatRoomException(ChatRoomErrorCode.CHATROOM_NOT_FOUND));
		aiReviewService.publishToGitHub(room, messageId);
	}
}