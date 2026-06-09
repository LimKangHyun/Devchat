package project.backend.domain.github.app;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.backend.auth.app.AuthTokenService;
import project.backend.domain.aireview.app.AiReviewPublishService;
import project.backend.domain.aireview.app.AiReviewService;
import project.backend.domain.chat.chatmessage.dao.ChatMessageRepository;
import project.backend.domain.chat.chatmessage.dto.ChatMessageResponse;
import project.backend.domain.chat.chatmessage.entity.ChatMessage;
import project.backend.domain.chat.chatmessage.mapper.ChatMessageMapper;
import project.backend.domain.chat.chatroom.dao.ChatRoomRedisRepository;
import project.backend.domain.chat.chatroom.dao.ChatRoomRepository;
import project.backend.domain.chat.chatroom.entity.ChatRoom;
import project.backend.domain.aireview.client.GeminiClient;
import project.backend.domain.github.client.GitHubBotClient;
import project.backend.domain.github.client.GitHubUserClient;
import project.backend.domain.github.GitRepoUrlUtils;
import project.backend.domain.github.dto.GitMessageDto;
import project.backend.domain.github.dto.GitRepoDto;
import project.backend.domain.aireview.entity.PrStatus;
import project.backend.domain.aireview.event.AiReviewRequestedEvent;
import project.backend.domain.github.mapper.GitMessageMapper;
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
	private final AiReviewPublishService aiReviewPublishService;
	private final GeminiClient geminiClient;
	private final GitHubBotClient gitHubBotClient;
	private final GitMessageMapper gitMessageMapper;
	private final ApplicationEventPublisher eventPublisher;

	@Value("${url.webhook-url}")
	private String webhookUrl;

	@Value("${github.username}")
	private String githubBotUsername;

	@Value("${github.bot.username}")
	private String aiReviewBotUsername;

	@Transactional
	public void handleEvent(Long roomId, String eventType, Map<String, Object> payload) {
		if (isBotReviewEvent(eventType, payload)) return;

		if ("pull_request".equals(eventType)) {
			handlePrStatusUpdate(roomId, payload);
		}

		GitMessageDto gitMessage = toGitMessage(eventType, payload);
		if (gitMessage == null) return;

		ChatRoom room = findRoom(roomId);
		sendGitMessage(room, gitMessage.withRoom(room));

		if ("pull_request".equals(eventType)) {
			handlePullRequestMessage(room, payload, gitMessage);
			handlePrEditedMessage(room, payload, gitMessage);
			handlePrSynchronizeMessage(room, payload, gitMessage);
		}
	}

	private boolean isBotReviewEvent(String eventType, Map<String, Object> payload) {
		if (!"pull_request_review".equals(eventType)) return false;

		Map<String, Object> sender = (Map<String, Object>) payload.get("sender");
		if (sender == null) return false;

		String login = (String) sender.get("login");
		return githubBotUsername.equalsIgnoreCase(login)
				|| aiReviewBotUsername.equalsIgnoreCase(login)
				|| (login != null && login.endsWith("[bot]"));
	}

	private void handlePrStatusUpdate(Long roomId, Map<String, Object> payload) {
		Map<String, Object> pr = (Map<String, Object>) payload.get("pull_request");
		String action  = (String) payload.get("action");
		int prNumber   = (int) pr.get("number");
		boolean merged = Boolean.TRUE.equals(pr.get("merged"));

		PrStatus prStatus;
		try {
			prStatus = PrStatus.from(action, merged);
		} catch (IllegalArgumentException e) {
			return;
		}

		switch (prStatus) {
			case OPEN, CLOSED, MERGED, REOPENED -> aiReviewService.updatePrStatus(roomId, prNumber, prStatus);
			case EDITED -> {
				String prTitle = (String) pr.get("title");
				String prBody  = (String) pr.getOrDefault("body", "");
				aiReviewService.updatePrInfo(roomId, prNumber, prTitle, prBody);
			}
		}
	}

	// OPEN일 때 AI 리뷰 pending 생성 (sendGitMessage 이후 호출)
	private void handlePullRequestMessage(ChatRoom room, Map<String, Object> payload, GitMessageDto gitMessage) {
		if (gitMessage.getPrStatus() != PrStatus.OPEN) return;

		Map<String, Object> pr = (Map<String, Object>) payload.get("pull_request");
		int prNumber   = (int) pr.get("number");
		String headSha = (String) ((Map<String, Object>) pr.get("head")).get("sha");
		String baseSha = (String) ((Map<String, Object>) pr.get("base")).get("sha");
		String prTitle = (String) pr.get("title");
		String prBody  = (String) pr.getOrDefault("body", "");

		aiReviewService.createPendingAndMessage(room, prNumber, headSha, aiReviewBotUsername, prTitle, prBody);
		eventPublisher.publishEvent(new AiReviewRequestedEvent(room, prNumber, headSha, baseSha));
	}

	// EDITED일 때 AI 리뷰 최초 생성 (레포 연결 후 첫 edited 이벤트) (sendGitMessage 이후 호출)
	private void handlePrEditedMessage(ChatRoom room, Map<String, Object> payload, GitMessageDto gitMessage) {
		if (gitMessage.getPrStatus() != PrStatus.EDITED) return;

		Map<String, Object> pr = (Map<String, Object>) payload.get("pull_request");
		int prNumber   = (int) pr.get("number");
		String prTitle = (String) pr.get("title");
		String prBody  = (String) pr.getOrDefault("body", "");

		boolean exists = aiReviewService.existsByRoomIdAndPrNumber(room.getId(), prNumber);
		log.info("EDITED 이벤트 - roomId={}, prNumber={}, exists={}", room.getId(), prNumber, exists);
		if (exists) return;

		GitRepoDto repo = GitRepoUrlUtils.validateAndParseUrl(room.getRepositoryUrl());
		String headSha = gitHubBotClient.getHeadSha(repo.ownerName(), repo.repoName(), prNumber);
		String baseSha = gitHubBotClient.getBaseSha(repo.ownerName(), repo.repoName(), prNumber);
		aiReviewService.createPendingAndMessage(room, prNumber, headSha, aiReviewBotUsername, prTitle, prBody);
		eventPublisher.publishEvent(new AiReviewRequestedEvent(room, prNumber, headSha, baseSha));
	}

	private GitMessageDto toGitMessage(String eventType, Map<String, Object> payload) {
		return switch (eventType) {
			case "issues"              -> gitMessageMapper.fromIssue(payload);
			case "pull_request"        -> gitMessageMapper.fromPullRequest(payload);
			case "pull_request_review" -> gitMessageMapper.fromPullRequestReview(payload);
			case "workflow_run"        -> gitMessageMapper.fromWorkflowRun(payload);
			default                    -> null;
		};
	}

	private void sendGitMessage(ChatRoom room, GitMessageDto gitMessage) {
		Member githubBot = memberService.getMemberByUsername(githubBotUsername);
		chatRoomRedisRepository.genMessageSeq(room.getId());

		String summarized = geminiClient.summarizeGitEvent(gitMessage);
		gitMessage.updateContent(gitMessage.getContent() + "\n\n" + summarized);

		ChatMessage message = chatMessageMapper.toEntityWithGit(gitMessage, githubBot);
		chatMessageRepository.save(message);

		ChatMessageResponse response = chatMessageMapper.toGitResponse(message);
		messagingTemplate.convertAndSend("/topic/chat/" + room.getId(), response);
	}

	private void handlePrSynchronizeMessage(ChatRoom room, Map<String, Object> payload, GitMessageDto gitMessage) {
		if (gitMessage.getPrStatus() != PrStatus.SYNCHRONIZE) return;

		Map<String, Object> pr = (Map<String, Object>) payload.get("pull_request");
		int prNumber   = (int) pr.get("number");
		String headSha = (String) ((Map<String, Object>) pr.get("head")).get("sha");
		String baseSha = (String) ((Map<String, Object>) pr.get("base")).get("sha");

		if (!aiReviewService.existsByRoomIdAndPrNumber(room.getId(), prNumber)) return;

		eventPublisher.publishEvent(new AiReviewRequestedEvent(room, prNumber, headSha, baseSha));
	}

	private ChatRoom findRoom(Long roomId) {
		return chatRoomRepository.findById(roomId)
				.orElseThrow(() -> new ChatRoomException(ChatRoomErrorCode.CHATROOM_NOT_FOUND));
	}

	public void registerWebhook(String repoUrl, Long roomId, Long memberId) {
		GitRepoDto gitRepoDto = GitRepoUrlUtils.validateAndParseUrl(repoUrl);

		String githubAccessToken = authTokenService.getGithubAccessToken(memberId);
		String webhookUrl = makeWebhookUrl(roomId);

		gitHubUserClient.validateAdminPermission(githubAccessToken, gitRepoDto.ownerName(), gitRepoDto.repoName());
		Long webhookId = gitHubUserClient.registerWebhook(githubAccessToken, gitRepoDto.ownerName(), gitRepoDto.repoName(), webhookUrl);

		findRoom(roomId).updateWebhookId(webhookId);
	}

	private String makeWebhookUrl(Long roomId) {
		return webhookUrl + "/github/webhook/" + roomId;
	}

	public void deleteWebhook(ChatRoom room, Long ownerId) {
		if (room.getRepositoryUrl() == null || room.getWebhookId() == null) {
			log.info("GitHub 연동이 없는 채팅방입니다. 웹훅 삭제 스킵: roomId={}", room.getId());
			return;
		}

		GitRepoDto gitRepoDto = GitRepoUrlUtils.validateAndParseUrl(room.getRepositoryUrl());
		String githubAccessToken = authTokenService.getGithubAccessToken(ownerId);
		gitHubUserClient.deleteWebhook(githubAccessToken, gitRepoDto.ownerName(), gitRepoDto.repoName(), room.getWebhookId());
	}

	public void publishAiReview(Long roomId, Long aiReviewId, String approverUsername) {
		aiReviewPublishService.publishToGitHub(findRoom(roomId), aiReviewId, approverUsername);
	}

	public void retryAiReview(Long roomId, int prNumber) {
		ChatRoom room = findRoom(roomId);
		GitRepoDto repo = GitRepoUrlUtils.validateAndParseUrl(room.getRepositoryUrl());
		String headSha = gitHubBotClient.getHeadSha(repo.ownerName(), repo.repoName(), prNumber);
		String baseSha = gitHubBotClient.getBaseSha(repo.ownerName(), repo.repoName(), prNumber);
		aiReviewService.triggerAiReview(room, prNumber, headSha, baseSha);
	}
}