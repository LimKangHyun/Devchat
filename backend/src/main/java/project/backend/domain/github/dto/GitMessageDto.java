package project.backend.domain.github.dto;

import java.util.Map;
import lombok.Builder;
import lombok.Getter;
import project.backend.domain.chat.chatroom.entity.ChatRoom;

@Getter
@Builder
public class GitMessageDto {

	private GitEventType type;
	private String actor;
	private String content;
	private String fullContent;
	private ChatRoom room;

	public static GitMessageDto fromIssue(Map<String, Object> payload) {
		String action = (String) payload.get("action");
		if (!action.equals("opened")) return null;

		Map<String, Object> issue = (Map<String, Object>) payload.get("issue");
		Map<String, Object> sender = (Map<String, Object>) payload.get("sender");

		String title = (String) issue.get("title");
		String url = (String) issue.get("html_url");
		String author = (String) sender.get("login");
		String body = (String) issue.get("body");

		String content = "[ISSUE " + action + "] " + title + " by " + author + "\n" + url;

		String fullContent = "제목: " + title + "\n"
				+ "작성자: " + author + "\n"
				+ (body != null ? "내용: " + body : "내용: 없음") + "\n"
				+ "URL: " + url;

		return GitMessageDto.of(GitEventType.ISSUE_OPEN, author, content, fullContent);
	}

	public static GitMessageDto fromPullRequest(Map<String, Object> payload) {
		String action = (String) payload.get("action");
		Map<String, Object> pr = (Map<String, Object>) payload.get("pull_request");
		Map<String, Object> sender = (Map<String, Object>) payload.get("sender");

		String title = (String) pr.get("title");
		String url = (String) pr.get("html_url");
		String author = (String) sender.get("login");
		String body = (String) pr.get("body");

		String content;
		String fullContent;
		GitEventType type;

		if (action.equals("opened")) {
			content = "[PR opened] " + title + " by " + author + "\n" + url;
			fullContent = "제목: " + title + "\n"
					+ "작성자: " + author + "\n"
					+ (body != null ? "내용: " + body : "내용: 없음") + "\n"
					+ "URL: " + url;
			type = GitEventType.PR_OPEN;
		} else if (action.equals("closed") && Boolean.TRUE.equals(pr.get("merged"))) {
			String fromBranch = ((Map<String, Object>) pr.get("head")).get("ref").toString();
			String toBranch = ((Map<String, Object>) pr.get("base")).get("ref").toString();
			content = "[PR merged] " + title + " by " + author + "\n"
					+ "merged to " + toBranch + " from " + fromBranch + "\n" + url;
			fullContent = "제목: " + title + "\n"
					+ "작성자: " + author + "\n"
					+ fromBranch + " → " + toBranch + " 머지\n"
					+ (body != null ? "내용: " + body : "내용: 없음") + "\n"
					+ "URL: " + url;
			type = GitEventType.PR_MERGED;
		} else {
			return null;
		}

		return GitMessageDto.of(type, author, content, fullContent);
	}

	public static GitMessageDto fromPullRequestReview(Map<String, Object> payload) {
		String action = (String) payload.get("action");
		if (!action.equals("submitted")) return null;

		Map<String, Object> review = (Map<String, Object>) payload.get("review");
		Map<String, Object> pr = (Map<String, Object>) payload.get("pull_request");

		String reviewer = (String) ((Map<String, Object>) review.get("user")).get("login");
		String state = (String) review.get("state");
		String reviewUrl = (String) review.get("html_url");
		String prTitle = (String) pr.get("title");
		String body = (String) review.get("body");

		String content = "[PR review: " + state + "] " + prTitle + " review by " + reviewer +
				(body != null ? "\n" + body : "") + "\n" + reviewUrl;

		String fullContent = "PR 제목: " + prTitle + "\n"
				+ "리뷰어: " + reviewer + "\n"
				+ "상태: " + state + "\n"
				+ (body != null ? "리뷰 내용: " + body : "리뷰 내용: 없음") + "\n"
				+ "URL: " + reviewUrl;

		return GitMessageDto.of(GitEventType.PR_REVIEW, reviewer, content, fullContent);
	}

	public static GitMessageDto fromWorkflowRun(Map<String, Object> payload) {
		Map<String, Object> workflowRun = (Map<String, Object>) payload.get("workflow_run");
		if (workflowRun == null) return null;

		String action = (String) payload.get("action");
		if (!"completed".equals(action)) return null;

		String name = (String) workflowRun.get("name");
		String conclusion = (String) workflowRun.get("conclusion");
		String url = (String) workflowRun.get("html_url");
		String branch = (String) workflowRun.get("head_branch");
		Map<String, Object> actor = (Map<String, Object>) workflowRun.get("triggering_actor");
		String triggerBy = actor != null ? (String) actor.get("login") : "unknown";

		String emoji = switch (conclusion) {
			case "success" -> "✅";
			case "failure" -> "❌";
			case "cancelled" -> "⚠️";
			default -> "❓";
		};

		String content = emoji + " [" + name + "] " + conclusion.toUpperCase()
				+ " by " + triggerBy
				+ " (branch: " + branch + ")"
				+ "\n" + url;

		String fullContent = "워크플로우: " + name + "\n"
				+ "결과: " + conclusion + "\n"
				+ "브랜치: " + branch + "\n"
				+ "실행자: " + triggerBy + "\n"
				+ "URL: " + url;

		return GitMessageDto.of(GitEventType.WORKFLOW_RUN, triggerBy, content, fullContent);
	}

	public static GitMessageDto of(GitEventType type, String actor, String content, String fullContent) {
		return GitMessageDto.builder()
				.type(type)
				.actor(actor)
				.content(content)
				.fullContent(fullContent)
				.build();
	}

	public static GitMessageDto of(GitEventType type, String actor, String content) {
		return GitMessageDto.builder()
				.type(type)
				.actor(actor)
				.content(content)
				.build();
	}

	public GitMessageDto attachRoom(GitMessageDto gitMessage, ChatRoom room) {
		gitMessage.room = room;
		return gitMessage;
	}

	public void updateContent(String content) {
		this.content = content;
	}
}