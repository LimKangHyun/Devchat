package project.api.domain.feedback.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import project.api.domain.feedback.app.FeedbackWebhookHandler.Outcome;
import project.api.domain.feedback.dao.FeedbackIssueRepository;
import project.api.domain.feedback.entity.FeedbackIssue;
import project.api.domain.github.webhook.GitHubWebhookVerifier;
import project.api.domain.member.entity.Member;
import project.api.domain.notification.entity.NotificationType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FeedbackWebhookHandlerTest {

    private static final String REPO = "LimKangHyun/Devchat";

    private final GitHubWebhookVerifier verifier = mock(GitHubWebhookVerifier.class);
    private final FeedbackIssueRepository issues = mock(FeedbackIssueRepository.class);
    private final FeedbackNotifier notifier = mock(FeedbackNotifier.class);
    private final FeedbackWebhookHandler handler =
            new FeedbackWebhookHandler(verifier, issues, notifier, new ObjectMapper(), REPO);

    private final Member sender = mock(Member.class);

    @BeforeEach
    void setUp() {
        when(verifier.isConfigured()).thenReturn(true);
        when(verifier.verify(any(), any())).thenReturn(GitHubWebhookVerifier.Result.VALID);
        FeedbackIssue issue = mock(FeedbackIssue.class);
        when(issue.getMember()).thenReturn(sender);
        when(issue.getIssueNumber()).thenReturn(98);
        when(issue.getTitle()).thenReturn("[버그] 채팅하면서 보내고 나서 문자가 하나씩 남아요");
        when(issues.findWithMemberByIssueNumber(98)).thenReturn(Optional.of(issue));
    }

    private static byte[] json(String s) {
        return s.replace('\'', '"').getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] comment(String association, String userType, String body) {
        return json("{'action':'created','repository':{'full_name':'" + REPO + "'},"
                + "'issue':{'number':98,'html_url':'https://github.com/" + REPO + "/issues/98'},"
                + "'comment':{'body':'" + body + "','author_association':'" + association + "',"
                + "'html_url':'https://github.com/" + REPO + "/issues/98#issuecomment-1',"
                + "'user':{'type':'" + userType + "'}}}");
    }

    private static byte[] closed(String reason) {
        return json("{'action':'closed','repository':{'full_name':'" + REPO + "'},"
                + "'issue':{'number':98,'state_reason':'" + reason + "',"
                + "'html_url':'https://github.com/" + REPO + "/issues/98'}}");
    }

    @Test
    void 관리자가_댓글을_달면_보낸_사람에게_댓글_링크와_함께_알린다() {
        Outcome out = handler.receive("issue_comment", comment("OWNER", "User", "고쳤어요! 확인 부탁드려요"), "sig");

        assertThat(out).isEqualTo(Outcome.HANDLED);
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(notifier).notify(eq(sender), eq(NotificationType.FEEDBACK_REPLIED), eq(98),
                message.capture(), eq("https://github.com/" + REPO + "/issues/98#issuecomment-1"));
        assertThat(message.getValue()).isEqualTo("보내 주신 피드백에 답장이 왔어요: 고쳤어요! 확인 부탁드려요");
    }

    @Test
    void 관리자가_아닌_사람이나_봇의_댓글은_알리지_않는다() {
        assertThat(handler.receive("issue_comment", comment("NONE", "User", "저도요"), "sig")).isEqualTo(Outcome.IGNORED);
        assertThat(handler.receive("issue_comment", comment("CONTRIBUTOR", "User", "저도요"), "sig")).isEqualTo(Outcome.IGNORED);
        assertThat(handler.receive("issue_comment", comment("OWNER", "Bot", "자동 댓글"), "sig")).isEqualTo(Outcome.IGNORED);
        verifyNoInteractions(notifier);
    }

    @Test
    void 이슈가_처리돼_닫히면_처리됐다고_알린다() {
        Outcome out = handler.receive("issues", closed("completed"), "sig");

        assertThat(out).isEqualTo(Outcome.HANDLED);
        verify(notifier).notify(eq(sender), eq(NotificationType.FEEDBACK_RESOLVED), eq(98),
                eq("보내 주신 피드백이 처리됐어요: 채팅하면서 보내고 나서 문자가 하나씩 남아요"),
                eq("https://github.com/" + REPO + "/issues/98"));
    }

    @Test
    void 처리하지_않기로_하고_닫으면_검토를_마쳤다고_알린다() {
        handler.receive("issues", closed("not_planned"), "sig");

        verify(notifier).notify(eq(sender), eq(NotificationType.FEEDBACK_RESOLVED), eq(98),
                eq("보내 주신 피드백 검토를 마쳤어요: 채팅하면서 보내고 나서 문자가 하나씩 남아요"), anyString());
    }

    @Test
    void 피드백으로_만든_이슈가_아니면_알리지_않는다() {
        when(issues.findWithMemberByIssueNumber(98)).thenReturn(Optional.empty());

        assertThat(handler.receive("issues", closed("completed"), "sig")).isEqualTo(Outcome.IGNORED);
        verifyNoInteractions(notifier);
    }

    @Test
    void 다른_저장소에서_온_이벤트는_무시한다() {
        byte[] other = json("{'action':'closed','repository':{'full_name':'someone/else'},"
                + "'issue':{'number':98,'state_reason':'completed','html_url':'x'}}");

        assertThat(handler.receive("issues", other, "sig")).isEqualTo(Outcome.IGNORED);
        verifyNoInteractions(notifier);
    }

    @Test
    void 서명이_틀리거나_secret이_없으면_거절한다() {
        when(verifier.verify(any(), any())).thenReturn(GitHubWebhookVerifier.Result.INVALID);
        assertThat(handler.receive("issues", closed("completed"), "bad")).isEqualTo(Outcome.REJECTED);

        when(verifier.isConfigured()).thenReturn(false);
        when(verifier.verify(any(), any())).thenReturn(GitHubWebhookVerifier.Result.NOT_CONFIGURED);
        assertThat(handler.receive("issues", closed("completed"), null)).isEqualTo(Outcome.REJECTED);
        verifyNoInteractions(notifier);
    }

    @Test
    void 긴_댓글은_줄여서_알린다() {
        String longBody = "가".repeat(300);
        handler.receive("issue_comment", comment("OWNER", "User", longBody), "sig");

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(notifier).notify(any(), any(), anyInt(), message.capture(), anyString());
        assertThat(message.getValue()).hasSizeLessThanOrEqualTo(200).endsWith("…");
    }

    @Test
    void ping과_다른_이벤트는_무시한다() {
        assertThat(handler.receive("ping", json("{'zen':'hi'}"), "sig")).isEqualTo(Outcome.IGNORED);
        assertThat(handler.receive("issues", json("{'action':'opened','repository':{'full_name':'" + REPO + "'}}"), "sig"))
                .isEqualTo(Outcome.IGNORED);
        verifyNoInteractions(notifier);
    }
}
