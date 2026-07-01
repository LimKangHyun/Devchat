package project.api.domain.github.app;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.api.auth.app.AuthTokenService;
import project.api.domain.aireview.app.AiReviewPublishService;
import project.api.domain.aireview.app.AiReviewService;
import project.api.domain.chat.chatmessage.dao.ChatMessageRepository;
import project.api.domain.chat.chatmessage.dto.ChatMessageResponse;
import project.api.domain.chat.chatmessage.entity.ChatMessage;
import project.api.domain.chat.chatmessage.mapper.ChatMessageMapper;
import project.api.domain.chat.chatroom.dao.ChatParticipantRepository;
import project.api.domain.chat.chatroom.dao.ChatRoomRedisRepository;
import project.api.domain.chat.chatroom.dao.ChatRoomRepository;
import project.api.domain.chat.chatroom.entity.ChatRoom;
import project.api.domain.github.client.GitHubBotClient;
import project.api.domain.github.client.GitHubUserClient;
import project.api.domain.github.GitRepoUrlUtils;
import project.api.domain.github.dto.GitMessageDto;
import project.api.domain.aireview.entity.PrStatus;
import project.api.domain.aireview.event.AiReviewRequestedEvent;
import project.api.domain.github.mapper.GitMessageMapper;
import project.api.domain.member.app.MemberService;
import project.api.domain.member.entity.Member;
import project.api.global.exception.errorcode.ChatRoomErrorCode;
import project.api.global.exception.ex.ChatRoomException;
import project.api.global.redis.RedisStreamClient;
import project.common.dto.github.GitRepoDto;

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
	private final GitHubBotClient gitHubBotClient;
	private final GitMessageMapper gitMessageMapper;
	private final ApplicationEventPublisher eventPublisher;
	private final ChatParticipantRepository chatParticipantRepository;
	private final RedisStreamClient redisStreamClient;

	@Value("${url.webhook-url}")
	private String webhookUrl;

	@Value("${github.username}")
	private String githubBotUsername;

	@Value("${github.bot.username}")
	private String aiReviewBotUsername;

	@Transactional
	public void handleEvent(Long roomId, String eventType, Map<String, Object> payload) {
		if (isBotReviewEvent(eventType, payload)) return;

		ChatRoom room = findRoom(roomId);

		if ("pull_request".equals(eventType)) {
			handlePrStatusUpdate(room, payload);
		}

		GitMessageDto gitMessage = toGitMessage(eventType, payload);
		if (gitMessage == null) return;

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

	private void handlePrStatusUpdate(ChatRoom room, Map<String, Object> payload) {
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
			case OPEN, CLOSED, REOPENED -> aiReviewService.updatePrStatus(room.getId(), prNumber, prStatus);
			case MERGED -> {
				aiReviewService.updatePrStatus(room.getId(), prNumber, prStatus);
				handlePrMerged(room, pr);
			}
			case EDITED -> {
				String prTitle = (String) pr.get("title");
				String prBody  = (String) pr.getOrDefault("body", "");
				aiReviewService.updatePrInfo(room.getId(), prNumber, prTitle, prBody);
			}
		}
	}

	private void handlePrMerged(ChatRoom room, Map<String, Object> pr) {
		int prNumber = (int) pr.get("number");
		String headSha = (String) ((Map<String, Object>) pr.get("head")).get("sha");

		Long ownerId = chatParticipantRepository.findByChatRoomIdAndIsOwnerTrue(room.getId())
			.map(cp -> cp.getParticipant().getId())
			.orElse(null);

		if (ownerId == null) {
			log.warn("채팅방 owner 없음, 재인덱싱 스킵. roomId={}", room.getId());
			return;
		}

		GitRepoDto repo = GitRepoUrlUtils.validateAndParseUrl(room.getRepositoryUrl());
		Map<String, String> fileStatuses = gitHubBotClient.getPrFileStatuses(
			repo.ownerName(), repo.repoName(), prNumber);

		for (Map.Entry<String, String> entry : fileStatuses.entrySet()) {
			String filePath = entry.getKey();
			String status = entry.getValue();
			if (!isReindexableFile(filePath)) continue;

			String fileContent = "removed".equals(status) ? ""
				: gitHubBotClient.getFileContent(repo.ownerName(), repo.repoName(), filePath, headSha);

			redisStreamClient.publishFileReindex(
				room.getId(), room.getRepositoryUrl(), ownerId,
				filePath, status, fileContent, headSha);
		}
	}

	private boolean isReindexableFile(String filePath) {
		return filePath.toLowerCase().endsWith(".java");
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

		GitRepoDto repo = GitRepoUrlUtils.validateAndParseUrl(room.getRepositoryUrl());
		String prDiff = gitHubBotClient.getPrDiff(repo.ownerName(), repo.repoName(), prNumber);

		Long aiReviewId = aiReviewService.createPendingAndMessage(room, prNumber, headSha,
				aiReviewBotUsername, prTitle, prBody, prDiff);
		eventPublisher.publishEvent(new AiReviewRequestedEvent(aiReviewId, headSha, baseSha));
	}

	// EDITED일 때 AI 리뷰 최초 생성 (레포 연결 후 첫 edited 이벤트) (sendGitMessage 이후 호출)
	private void handlePrEditedMessage(ChatRoom room, Map<String, Object> payload, GitMessageDto gitMessage) {
		if (gitMessage.getPrStatus() != PrStatus.EDITED) return;

		Map<String, Object> pr = (Map<String, Object>) payload.get("pull_request");
		int prNumber = (int) pr.get("number");
		String prTitle = (String) pr.get("title");
		String prBody = (String) pr.getOrDefault("body", "");

		boolean exists = aiReviewService.existsByRoomIdAndPrNumber(room.getId(), prNumber);
		if (exists) return;

		GitRepoDto repo = GitRepoUrlUtils.validateAndParseUrl(room.getRepositoryUrl());
		String headSha = gitHubBotClient.getHeadSha(repo.ownerName(), repo.repoName(), prNumber);
		String baseSha = gitHubBotClient.getBaseSha(repo.ownerName(), repo.repoName(), prNumber);
		String prDiff = gitHubBotClient.getPrDiff(repo.ownerName(), repo.repoName(), prNumber);

		Long aiReviewId = aiReviewService.createPendingAndMessage(room, prNumber, headSha,
				aiReviewBotUsername, prTitle, prBody, prDiff);
		eventPublisher.publishEvent(new AiReviewRequestedEvent(aiReviewId, headSha, baseSha));
	}

	private GitMessageDto toGitMessage(String eventType, Map<String, Object> payload) {
		return switch (eventType) {
			case "issues"              -> gitMessageMapper.fromIssue(payload);
			case "pull_request"        -> gitMessageMapper.fromPullRequest(payload);
			case "pull_request_review" -> gitMessageMapper.fromPullRequestReview(payload);
			case "workflow_run"        -> gitMessageMapper.fromWorkflowRun(payload);
			case "push"                -> gitMessageMapper.fromPush(payload);
			default                    -> null;
		};
	}

	private void sendGitMessage(ChatRoom room, GitMessageDto gitMessage) {
		Member githubBot = memberService.getMemberByUsername(githubBotUsername);
		chatRoomRedisRepository.genMessageSeq(room.getId());

		// TODO: AI 요약은 Worker로 이전 예정
		// if (room.isAiSummaryEnabled()) { ... }

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

		GitRepoDto repo = GitRepoUrlUtils.validateAndParseUrl(room.getRepositoryUrl());
		String prDiff = gitHubBotClient.getPrDiff(repo.ownerName(), repo.repoName(), prNumber);

		Long aiReviewId = aiReviewService.resetToPendingAndPublish(room, prNumber, headSha, prDiff);
		eventPublisher.publishEvent(new AiReviewRequestedEvent(aiReviewId, headSha, baseSha));
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

	public void deleteWebhook(String repositoryUrl, Long webhookId, Long ownerId) {
		if (repositoryUrl == null || webhookId == null) {
			log.info("웹훅 삭제 스킵. repositoryUrl 또는 webhookId 없음");
			return;
		}
		GitRepoDto gitRepoDto = GitRepoUrlUtils.validateAndParseUrl(repositoryUrl);
		String githubAccessToken = authTokenService.getGithubAccessToken(ownerId);
		gitHubUserClient.deleteWebhook(githubAccessToken, gitRepoDto.ownerName(), gitRepoDto.repoName(), webhookId);
		log.info("Github webhook 삭제. webhook={}", webhookId);
	}

	public void publishAiReview(Long roomId, Long aiReviewId, String approverUsername) {
		aiReviewPublishService.publishToGitHub(findRoom(roomId), aiReviewId, approverUsername);
	}

	public void retryAiReview(Long roomId, int prNumber) {
		// TODO: Stream 발행으로 교체
	}
}