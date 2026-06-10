package project.backend.domain.github.mapper;

import java.util.Map;
import org.springframework.stereotype.Component;
import project.backend.domain.github.dto.GitEventType;
import project.backend.domain.github.dto.GitMessageDto;
import project.backend.domain.aireview.entity.PrStatus;

@Component
public class GitMessageMapper {

    public GitMessageDto fromIssue(Map<String, Object> payload) {
        String action = (String) payload.get("action");
        if (!action.equals("opened")) return null;

        Map<String, Object> issue = (Map<String, Object>) payload.get("issue");
        Map<String, Object> sender = (Map<String, Object>) payload.get("sender");

        String title = (String) issue.get("title");
        String url = (String) issue.get("html_url");
        String author = (String) sender.get("login");
        String body = (String) issue.get("body");

        String content = "[ISSUE opened] " + title + " by " + author + "\n" + url;
        String fullContent = "제목: " + title + "\n"
                + "작성자: " + author + "\n"
                + (body != null ? "내용: " + body : "내용: 없음") + "\n"
                + "URL: " + url;

        return GitMessageDto.builder()
                .type(GitEventType.ISSUE)
                .actor(author)
                .content(content)
                .fullContent(fullContent)
                .build();
    }

    public GitMessageDto fromPullRequest(Map<String, Object> payload) {
        String action = (String) payload.get("action");
        Map<String, Object> pr = (Map<String, Object>) payload.get("pull_request");
        Map<String, Object> sender = (Map<String, Object>) payload.get("sender");

        boolean merged = Boolean.TRUE.equals(pr.get("merged"));
        PrStatus prStatus;
        try {
            prStatus = PrStatus.from(action, merged);
        } catch (IllegalArgumentException e) {
            return null;
        }

        if (!isChatTarget(prStatus)) return null;

        String title  = (String) pr.get("title");
        String url    = (String) pr.get("html_url");
        String author = (String) sender.get("login");
        String body   = (String) pr.get("body");

        return GitMessageDto.builder()
                .type(GitEventType.PULL_REQUEST)
                .prStatus(prStatus)
                .actor(author)
                .content(buildContent(prStatus, title, author, url, pr))
                .fullContent(buildFullContent(prStatus, title, author, body, url, pr))
                .build();
    }

    private boolean isChatTarget(PrStatus prStatus) {
        return switch (prStatus) {
            case OPEN, MERGED, EDITED, REOPENED, SYNCHRONIZE -> true;
            default -> false;
        };
    }

    private String buildContent(PrStatus prStatus, String title, String author, String url, Map<String, Object> pr) {
        if (prStatus == PrStatus.MERGED) {
            String fromBranch = ((Map<String, Object>) pr.get("head")).get("ref").toString();
            String toBranch   = ((Map<String, Object>) pr.get("base")).get("ref").toString();
            return "[PR merged] " + title + " by " + author + "\n"
                    + "merged to " + toBranch + " from " + fromBranch + "\n" + url;
        }
        String actionLabel = switch (prStatus) {
            case OPEN        -> "opened";
            case EDITED      -> "edited";
            case REOPENED    -> "reopened";
            case SYNCHRONIZE -> "updated";
            default          -> prStatus.name().toLowerCase();
        };
        return "[PR " + actionLabel + "] " + title + " by " + author + "\n" + url;
    }

    private String buildFullContent(PrStatus prStatus, String title, String author, String body, String url, Map<String, Object> pr) {
        String base = "제목: " + title + "\n"
                + "작성자: " + author + "\n";

        String middle = switch (prStatus) {
            case MERGED -> {
                String fromBranch = ((Map<String, Object>) pr.get("head")).get("ref").toString();
                String toBranch   = ((Map<String, Object>) pr.get("base")).get("ref").toString();
                yield fromBranch + " → " + toBranch + " 머지\n";
            }
            case SYNCHRONIZE -> "새 커밋이 푸시되어 PR이 업데이트되었습니다.\n";
            default -> "";
        };

        return base + middle
                + (body != null ? "내용: " + body : "내용: 없음") + "\n"
                + "URL: " + url;
    }

    public GitMessageDto fromPullRequestReview(Map<String, Object> payload) {
        String action = (String) payload.get("action");
        if (!action.equals("submitted")) return null;

        Map<String, Object> review = (Map<String, Object>) payload.get("review");
        Map<String, Object> pr = (Map<String, Object>) payload.get("pull_request");

        String reviewer  = (String) ((Map<String, Object>) review.get("user")).get("login");
        String state     = (String) review.get("state");
        String reviewUrl = (String) review.get("html_url");
        String prTitle   = (String) pr.get("title");
        String body      = (String) review.get("body");

        String content = "[PR review: " + state + "] " + prTitle + " review by " + reviewer
                + (body != null ? "\n" + body : "") + "\n" + reviewUrl;
        String fullContent = "PR 제목: " + prTitle + "\n"
                + "리뷰어: " + reviewer + "\n"
                + "상태: " + state + "\n"
                + (body != null ? "리뷰 내용: " + body : "리뷰 내용: 없음") + "\n"
                + "URL: " + reviewUrl;

        return GitMessageDto.builder()
                .type(GitEventType.PULL_REQUEST)
                .actor(reviewer)
                .content(content)
                .fullContent(fullContent)
                .build();
    }

    public GitMessageDto fromWorkflowRun(Map<String, Object> payload) {
        Map<String, Object> workflowRun = (Map<String, Object>) payload.get("workflow_run");
        if (workflowRun == null) return null;

        String action = (String) payload.get("action");
        if (!"completed".equals(action)) return null;

        String name       = (String) workflowRun.get("name");
        String conclusion = (String) workflowRun.get("conclusion");
        String url        = (String) workflowRun.get("html_url");
        String branch     = (String) workflowRun.get("head_branch");
        Map<String, Object> actor = (Map<String, Object>) workflowRun.get("triggering_actor");
        String triggerBy  = actor != null ? (String) actor.get("login") : "unknown";

        String emoji = switch (conclusion) {
            case "success"   -> "✅";
            case "failure"   -> "❌";
            case "cancelled" -> "⚠️";
            default          -> "❓";
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

        return GitMessageDto.builder()
                .type(GitEventType.WORKFLOW_RUN)
                .actor(triggerBy)
                .content(content)
                .fullContent(fullContent)
                .build();
    }
}