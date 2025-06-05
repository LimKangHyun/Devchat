package project.backend.domain.chat.github.dto;

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
	private ChatRoom room;

	public static GitMessageDto fromIssue(Map<String, Object> payload) {
		String action = (String) payload.get("action");

		//오픈된 이슈만 처리
		if (!action.equals("opened")) {
			return null;
		}

		Map<String, Object> issue = (Map<String, Object>) payload.get("issue");
		Map<String, Object> sender = (Map<String, Object>) payload.get("sender");

		String title = (String) issue.get("title");
		String url = (String) issue.get("html_url");
		String author = (String) sender.get("login");

		String content = "[ISSUE " + action + "] " + title + " by " + author + "\n" + url;

		return GitMessageDto.of(GitEventType.ISSUE_OPEN, author,
			content);
	}

	public static GitMessageDto fromPullRequest(Map<String, Object> payload) {
		String action = (String) payload.get("action");
		Map<String, Object> pr = (Map<String, Object>) payload.get("pull_request");
		Map<String, Object> sender = (Map<String, Object>) payload.get("sender");

		String title = (String) pr.get("title");
		String url = (String) pr.get("html_url");
		String author = (String) sender.get("login");

		String content;
		GitEventType type;

		if (action.equals("opened")) { //pr이 open된 경우
			content = "[PR opened] " + title + " by " + author + "\n" + url;
			type = GitEventType.PR_OPEN;
		} else if (action.equals("closed") && Boolean.TRUE.equals(
			pr.get("merged"))) { //pr이 merge된 경우
			String fromBranch = ((Map<String, Object>) pr.get("head")).get("ref").toString();
			String toBranch = ((Map<String, Object>) pr.get("base")).get("ref").toString();

			content = "[PR merged] " + title + " by " + author + "\n"
				+ "merged to " + toBranch + " from " + fromBranch + "\n"
				+ url;
			type = GitEventType.PR_MERGED;
		} else {
			return null;
		}

		return GitMessageDto.of(type, author, content);
	}

	public static GitMessageDto fromPullRequestReview(Map<String, Object> payload) {
		String action = (String) payload.get("action");
		if (!action.equals("submitted")) {
			return null;
		}

		Map<String, Object> review = (Map<String, Object>) payload.get("review");
		Map<String, Object> pr = (Map<String, Object>) payload.get("pull_request");

		String reviewer = (String) ((Map<String, Object>) review.get("user")).get("login");
		String state = (String) review.get("state"); // approved, commented, changes_requested
		String reviewUrl = (String) review.get("html_url");
		String prTitle = (String) pr.get("title");
		String body = (String) review.get("body");

		String content = "[PR review: " + state + "] " + prTitle + " review by " + reviewer +
			(body != null ? "\n" + body : "") + "\n" + reviewUrl;

		return GitMessageDto.of(GitEventType.PR_REVIEW, reviewer,
			content);
	}

	public static GitMessageDto of(GitEventType type, String actor,
		String content) {
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

}
