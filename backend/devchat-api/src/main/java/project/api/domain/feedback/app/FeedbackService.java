package project.api.domain.feedback.app;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import project.api.domain.feedback.client.GitHubIssueClient;
import project.api.domain.feedback.dao.FeedbackIssueRepository;
import project.api.domain.feedback.entity.FeedbackIssue;
import project.api.domain.member.dao.MemberRepository;
import project.api.domain.feedback.dto.FeedbackRequest;
import project.api.domain.feedback.dto.FeedbackResponse;
import project.api.global.exception.errorcode.FeedbackErrorCode;
import project.api.global.exception.ex.FeedbackException;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackService {

    // @사용자·@조직/팀 멘션. 공개 이슈로 아무나 남을 호출(알림)하지 못하게 @ 뒤에 보이지 않는 문자를 넣는다
    private static final Pattern MENTION = Pattern.compile("@(?=[A-Za-z0-9])");
    private static final String NO_MENTION = "@​";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final GitHubIssueClient issueClient;
    private final FeedbackRateLimiter rateLimiter;
    private final FeedbackIssueRepository feedbackIssueRepository;
    private final MemberRepository memberRepository;

    public FeedbackResponse submit(Long memberId, FeedbackRequest req) {
        if (!issueClient.isConfigured()) {
            throw new FeedbackException(FeedbackErrorCode.FEEDBACK_NOT_CONFIGURED);
        }
        if (!rateLimiter.tryAcquire(memberId)) {
            throw new FeedbackException(FeedbackErrorCode.TOO_MANY_FEEDBACK);
        }

        String title = "[" + req.type().getLabel() + "] " + noMention(oneLine(req.title()));
        String body = buildBody(req);
        try {
            GitHubIssueClient.CreatedIssue issue = issueClient.createIssue(title, body);
            log.info("피드백 이슈 생성 - memberId={}, issue=#{}", memberId, issue.number());
            remember(issue.number(), memberId, title);
            return new FeedbackResponse(issue.number(), issue.url());
        } catch (Exception e) {
            log.warn("피드백 이슈 생성 실패 - memberId={}, error={}", memberId, e.getMessage());
            throw new FeedbackException(FeedbackErrorCode.FEEDBACK_SEND_FAILED, e);
        }
    }

    // 이슈에 답장이 오면 보낸 사람에게 알리려고 기록한다. 실패해도 이슈는 이미 만들어졌으니 피드백은 성공으로 둔다
    private void remember(int issueNumber, Long memberId, String title) {
        try {
            feedbackIssueRepository.save(
                    new FeedbackIssue(issueNumber, memberRepository.getReferenceById(memberId), title));
        } catch (Exception e) {
            log.warn("피드백 이슈 기록 실패 - memberId={}, issue=#{}, error={}", memberId, issueNumber, e.getMessage());
        }
    }

    // 공개 저장소의 이슈라 누가 보냈는지(사용자 번호)는 적지 않는다. 보낸 사람은 서버 로그의 memberId·이슈 번호로 찾는다.
    // 브라우저 정보도 원문 대신 기기·OS·브라우저만 줄여 적는다
    private String buildBody(FeedbackRequest req) {
        StringBuilder sb = new StringBuilder();
        sb.append(noMention(req.content().strip())).append("\n\n---\n");
        sb.append("- 종류: ").append(req.type().getLabel()).append('\n');
        if (hasText(req.pagePath())) sb.append("- 화면: `").append(codeSafe(req.pagePath())).append("`\n");
        String device = UserAgentSummary.of(req.userAgent());
        if (device != null) sb.append("- 기기: ").append(device).append('\n');
        sb.append("- 보낸 시각: ").append(ZonedDateTime.now(ZoneId.of("Asia/Seoul")).format(TIME)).append(" (KST)\n");
        sb.append("\n_DevChat 앱의 피드백 보내기로 등록된 이슈입니다._");
        return sb.toString();
    }

    private static String noMention(String s) {
        return MENTION.matcher(s).replaceAll(NO_MENTION);
    }

    private static String oneLine(String s) {
        return s.replaceAll("\\s+", " ").strip();
    }

    // 인라인 코드 안에 넣을 값: 줄바꿈과 백틱을 없앤다
    private static String codeSafe(String s) {
        return oneLine(s).replace('`', '\'');
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
