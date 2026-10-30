package project.api.domain.feedback.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import project.api.domain.feedback.dao.FeedbackIssueRepository;
import project.api.domain.feedback.entity.FeedbackIssue;
import project.api.domain.github.webhook.GitHubWebhookVerifier;
import project.api.domain.notification.entity.NotificationType;

/**
 * DevChat 저장소 webhook(issue_comment·issues)을 받아, 피드백을 보낸 사람에게 알린다.
 * - 관리자(저장소 주인·멤버·협업자)의 댓글만 알린다. 공개 저장소라 누구나 댓글을 달 수 있어서다
 * - 이슈가 닫히면 처리됐다고(또는 검토를 마쳤다고) 알린다
 * 알림을 만드는 공개 주소라 서명이 맞는 요청만 받는다 (secret이 없으면 모두 거절).
 */
@Slf4j
@Component
public class FeedbackWebhookHandler {

    public enum Outcome { HANDLED, IGNORED, REJECTED }

    private static final Set<String> MAINTAINERS = Set.of("OWNER", "MEMBER", "COLLABORATOR");
    private static final int MESSAGE_MAX = 200;

    private final GitHubWebhookVerifier verifier;
    private final FeedbackIssueRepository feedbackIssueRepository;
    private final FeedbackNotifier notifier;
    private final ObjectMapper objectMapper;
    private final String repository;

    public FeedbackWebhookHandler(GitHubWebhookVerifier verifier,
                                  FeedbackIssueRepository feedbackIssueRepository,
                                  FeedbackNotifier notifier,
                                  ObjectMapper objectMapper,
                                  @Value("${feedback.github.repository:LimKangHyun/Devchat}") String repository) {
        this.verifier = verifier;
        this.feedbackIssueRepository = feedbackIssueRepository;
        this.notifier = notifier;
        this.objectMapper = objectMapper;
        this.repository = repository == null ? "" : repository.trim();
    }

    public Outcome receive(String event, byte[] body, String signature) {
        if (!verifier.isConfigured() || verifier.verify(body, signature) != GitHubWebhookVerifier.Result.VALID) {
            log.warn("[피드백 webhook] 서명이 맞지 않아 거절 event={}", event);
            return Outcome.REJECTED;
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            if (!repository.equalsIgnoreCase(root.path("repository").path("full_name").asText())) {
                return Outcome.IGNORED;
            }
            return switch (event == null ? "" : event) {
                case "issue_comment" -> onComment(root);
                case "issues" -> onIssue(root);
                default -> Outcome.IGNORED;
            };
        } catch (Exception e) {
            log.warn("[피드백 webhook] 처리 실패 event={}: {}", event, e.getMessage());
            return Outcome.IGNORED;
        }
    }

    private Outcome onComment(JsonNode root) {
        if (!"created".equals(root.path("action").asText())) return Outcome.IGNORED;
        JsonNode comment = root.path("comment");
        if ("Bot".equals(comment.path("user").path("type").asText())) return Outcome.IGNORED;
        if (!MAINTAINERS.contains(comment.path("author_association").asText())) return Outcome.IGNORED;

        int number = root.path("issue").path("number").asInt();
        return feedbackIssueRepository.findWithMemberByIssueNumber(number)
                .map(issue -> {
                    notifier.notify(issue.getMember(), NotificationType.FEEDBACK_REPLIED, number,
                            limit("보내 주신 피드백에 답장이 왔어요: " + oneLine(comment.path("body").asText())),
                            comment.path("html_url").asText(root.path("issue").path("html_url").asText()));
                    return Outcome.HANDLED;
                })
                .orElse(Outcome.IGNORED);
    }

    private Outcome onIssue(JsonNode root) {
        if (!"closed".equals(root.path("action").asText())) return Outcome.IGNORED;
        JsonNode issueNode = root.path("issue");
        int number = issueNode.path("number").asInt();
        boolean notPlanned = "not_planned".equals(issueNode.path("state_reason").asText());

        return feedbackIssueRepository.findWithMemberByIssueNumber(number)
                .map(issue -> {
                    String lead = notPlanned ? "보내 주신 피드백 검토를 마쳤어요: " : "보내 주신 피드백이 처리됐어요: ";
                    notifier.notify(issue.getMember(), NotificationType.FEEDBACK_RESOLVED, number,
                            limit(lead + withoutTypeLabel(issue)), issueNode.path("html_url").asText());
                    return Outcome.HANDLED;
                })
                .orElse(Outcome.IGNORED);
    }

    // 이슈 제목 앞의 "[버그] " 같은 종류 표시는 알림에서 뺀다
    private static String withoutTypeLabel(FeedbackIssue issue) {
        return issue.getTitle().replaceFirst("^\\[[^\\]]*\\]\\s*", "");
    }

    private static String oneLine(String s) {
        return s.replaceAll("\\s+", " ").strip();
    }

    private static String limit(String s) {
        return s.length() <= MESSAGE_MAX ? s : s.substring(0, MESSAGE_MAX - 1) + "…";
    }
}
