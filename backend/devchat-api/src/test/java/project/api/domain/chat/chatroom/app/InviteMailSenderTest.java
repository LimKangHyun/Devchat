package project.api.domain.chat.chatroom.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

class InviteMailSenderTest {

    private final JavaMailSender mailSender = mock(JavaMailSender.class);

    @SuppressWarnings("unchecked")
    private InviteMailSender sender(String from) {
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        given(provider.getIfAvailable()).willReturn(mailSender);
        given(mailSender.createMimeMessage()).willAnswer(i -> new MimeMessage(Session.getInstance(new Properties())));
        return new InviteMailSender(provider, from, "https://devchat.example.com/");
    }

    @Test
    @DisplayName("입장 링크는 프론트 주소 + /invite/초대코드")
    void inviteLink() {
        assertThat(sender("bot@gmail.com").inviteLink("abc123"))
            .isEqualTo("https://devchat.example.com/invite/abc123");
    }

    @Test
    @DisplayName("주소마다 한 통씩 보내고, 방 이름 등은 HTML 이스케이프한다")
    void sendsEachAndEscapes() throws Exception {
        sender("bot@gmail.com").send(new InviteMailSender.InviteMail(
            "<script>방</script>", "abc123", "림", List.of("a@example.com", "b@example.com")));

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, times(2)).send(captor.capture());
        String body = (String) captor.getValue().getContent();
        assertThat(body).contains("https://devchat.example.com/invite/abc123");
        assertThat(body).contains("&lt;script&gt;").doesNotContain("<script>방");
    }

    @Test
    @DisplayName("한 주소가 실패해도 나머지는 보낸다")
    void continuesOnFailure() {
        InviteMailSender s = sender("bot@gmail.com");
        willThrow(new MailSendException("fail")).willDoNothing().given(mailSender).send(any(MimeMessage.class));

        s.send(new InviteMailSender.InviteMail("방", "abc123", "림", List.of("a@example.com", "b@example.com")));

        verify(mailSender, times(2)).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("메일 계정이 비어 있으면 보내지 않는다(로그만)")
    void skipsWithoutAccount() {
        sender("").send(new InviteMailSender.InviteMail("방", "abc123", "림", List.of("a@example.com")));
        verify(mailSender, never()).send(any(MimeMessage.class));
    }
}
