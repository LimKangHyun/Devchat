package project.api.domain.feedback.app;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import project.api.domain.feedback.client.GitHubIssueClient;
import project.api.domain.feedback.dao.FeedbackIssueRepository;
import project.api.domain.feedback.entity.FeedbackIssue;
import project.api.domain.member.dao.MemberRepository;
import project.api.domain.member.entity.Member;
import project.api.domain.feedback.dto.FeedbackRequest;
import project.api.domain.feedback.dto.FeedbackResponse;
import project.api.domain.feedback.dto.FeedbackType;
import project.api.global.exception.errorcode.FeedbackErrorCode;
import project.api.global.exception.ex.FeedbackException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class FeedbackServiceTest {

    private final GitHubIssueClient issueClient = mock(GitHubIssueClient.class);
    private final FeedbackRateLimiter rateLimiter = mock(FeedbackRateLimiter.class);
    private final FeedbackIssueRepository feedbackIssueRepository = mock(FeedbackIssueRepository.class);
    private final MemberRepository memberRepository = mock(MemberRepository.class);
    private final FeedbackService service =
            new FeedbackService(issueClient, rateLimiter, feedbackIssueRepository, memberRepository);

    private FeedbackRequest request(String title, String content) {
        return new FeedbackRequest(FeedbackType.BUG, title, content, "/chat/abc", IPHONE_SAFARI);
    }

    private static final String IPHONE_SAFARI =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_6_1 like Mac OS X) AppleWebKit/605.1.15 "
                    + "(KHTML, like Gecko) Version/17.6 Mobile/15E148 Safari/604.1";

    @Test
    void 피드백을_종류가_붙은_이슈로_만든다() {
        when(issueClient.isConfigured()).thenReturn(true);
        when(rateLimiter.tryAcquire(7L)).thenReturn(true);
        when(issueClient.createIssue(anyString(), anyString()))
                .thenReturn(new GitHubIssueClient.CreatedIssue(12, "https://github.com/o/r/issues/12"));

        FeedbackResponse res = service.submit(7L, request("  전송이 안 돼요  ", "버튼을 눌러도\n안 가요"));

        ArgumentCaptor<String> title = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(issueClient).createIssue(title.capture(), body.capture());
        assertThat(title.getValue()).isEqualTo("[버그] 전송이 안 돼요");
        assertThat(body.getValue())
                .contains("버튼을 눌러도\n안 가요")
                .contains("/chat/abc")
                .contains("기기: iPhone · iOS 17.6 · Safari 17");
        assertThat(res.issueNumber()).isEqualTo(12);
    }

    @Test
    void 공개_이슈에는_사용자_번호와_브라우저_원문을_적지_않는다() {
        when(issueClient.isConfigured()).thenReturn(true);
        when(rateLimiter.tryAcquire(7L)).thenReturn(true);
        when(issueClient.createIssue(anyString(), anyString()))
                .thenReturn(new GitHubIssueClient.CreatedIssue(12, "https://github.com/o/r/issues/12"));

        FeedbackResponse res = service.submit(7L, request("제목", "내용"));

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(issueClient).createIssue(anyString(), body.capture());
        assertThat(body.getValue())
                .doesNotContain("사용자 번호")
                .doesNotContain("Mozilla")
                .doesNotContain("15E148");
        assertThat(res.issueNumber()).isEqualTo(12);
        assertThat(res.issueUrl()).isEqualTo("https://github.com/o/r/issues/12");
    }

    @Test
    void 답장이_오면_알리도록_이슈와_보낸_사람을_기록한다() {
        when(issueClient.isConfigured()).thenReturn(true);
        when(rateLimiter.tryAcquire(7L)).thenReturn(true);
        when(issueClient.createIssue(anyString(), anyString()))
                .thenReturn(new GitHubIssueClient.CreatedIssue(98, "u"));
        Member member = mock(Member.class);
        when(memberRepository.getReferenceById(7L)).thenReturn(member);

        service.submit(7L, request("글자가 남아요", "내용"));

        ArgumentCaptor<FeedbackIssue> saved = ArgumentCaptor.forClass(FeedbackIssue.class);
        verify(feedbackIssueRepository).save(saved.capture());
        assertThat(saved.getValue().getIssueNumber()).isEqualTo(98);
        assertThat(saved.getValue().getMember()).isSameAs(member);
        assertThat(saved.getValue().getTitle()).isEqualTo("[버그] 글자가 남아요");
    }

    @Test
    void 기록에_실패해도_피드백은_성공한다() {
        when(issueClient.isConfigured()).thenReturn(true);
        when(rateLimiter.tryAcquire(7L)).thenReturn(true);
        when(issueClient.createIssue(anyString(), anyString()))
                .thenReturn(new GitHubIssueClient.CreatedIssue(98, "u"));
        when(feedbackIssueRepository.save(any())).thenThrow(new RuntimeException("db down"));

        FeedbackResponse res = service.submit(7L, request("제목", "내용"));

        assertThat(res.issueNumber()).isEqualTo(98);
    }

    @Test
    void 내용의_멘션은_알림이_가지_않게_바꾼다() {
        when(issueClient.isConfigured()).thenReturn(true);
        when(rateLimiter.tryAcquire(7L)).thenReturn(true);
        when(issueClient.createIssue(anyString(), anyString()))
                .thenReturn(new GitHubIssueClient.CreatedIssue(1, "u"));

        service.submit(7L, request("@octocat 봐주세요", "@LimKangHyun 이거 @team/all"));

        ArgumentCaptor<String> title = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(issueClient).createIssue(title.capture(), body.capture());
        assertThat(body.getValue()).doesNotContainPattern("@[A-Za-z0-9]");
        assertThat(title.getValue()).doesNotContainPattern("@[A-Za-z0-9]");
    }

    @Test
    void 너무_자주_보내면_막는다() {
        when(issueClient.isConfigured()).thenReturn(true);
        when(rateLimiter.tryAcquire(7L)).thenReturn(false);

        assertThatThrownBy(() -> service.submit(7L, request("제목", "내용")))
                .isInstanceOf(FeedbackException.class)
                .extracting("errorCode").isEqualTo(FeedbackErrorCode.TOO_MANY_FEEDBACK);
        verify(issueClient, never()).createIssue(anyString(), anyString());
    }

    @Test
    void 토큰이_없으면_받지_않는다() {
        when(issueClient.isConfigured()).thenReturn(false);

        assertThatThrownBy(() -> service.submit(7L, request("제목", "내용")))
                .isInstanceOf(FeedbackException.class)
                .extracting("errorCode").isEqualTo(FeedbackErrorCode.FEEDBACK_NOT_CONFIGURED);
        verifyNoInteractions(rateLimiter);
    }
}
