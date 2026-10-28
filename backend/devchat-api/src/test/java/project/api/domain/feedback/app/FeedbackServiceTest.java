package project.api.domain.feedback.app;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import project.api.domain.feedback.client.GitHubIssueClient;
import project.api.domain.feedback.dto.FeedbackRequest;
import project.api.domain.feedback.dto.FeedbackResponse;
import project.api.domain.feedback.dto.FeedbackType;
import project.api.global.exception.errorcode.FeedbackErrorCode;
import project.api.global.exception.ex.FeedbackException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class FeedbackServiceTest {

    private final GitHubIssueClient issueClient = mock(GitHubIssueClient.class);
    private final FeedbackRateLimiter rateLimiter = mock(FeedbackRateLimiter.class);
    private final FeedbackService service = new FeedbackService(issueClient, rateLimiter);

    private FeedbackRequest request(String title, String content) {
        return new FeedbackRequest(FeedbackType.BUG, title, content, "/chat/abc", "Mozilla/5.0 (iPhone)");
    }

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
                .contains("사용자 번호: 7")
                .contains("/chat/abc")
                .contains("Mozilla/5.0 (iPhone)");
        assertThat(res.issueNumber()).isEqualTo(12);
        assertThat(res.issueUrl()).isEqualTo("https://github.com/o/r/issues/12");
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
