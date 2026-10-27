package project.api.domain.chat.chatroom.app;

import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriUtils;

/**
 * 채팅방 초대 메일 발송. 요청 스레드를 붙잡지 않도록 비동기로 보낸다.
 * 한 주소가 실패해도 나머지는 계속 보낸다(실패는 로그로만 남긴다).
 * 메일 계정(spring.mail.username)이 비어 있으면 개발 환경으로 보고 메일 대신 초대 링크를 로그에 남긴다.
 */
@Slf4j
@Component
public class InviteMailSender {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String from;
    private final String frontendUrl;

    public InviteMailSender(ObjectProvider<JavaMailSender> mailSenderProvider,
                            @Value("${spring.mail.username:}") String from,
                            @Value("${url.domain-url}") String frontendUrl) {
        this.mailSenderProvider = mailSenderProvider;
        this.from = from;
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
    }

    public record InviteMail(String roomName, String inviteCode, String inviterNickname, List<String> emails) {}

    @Async("chatRoomEventExecutor")
    public void send(InviteMail mail) {
        String link = inviteLink(mail.inviteCode());
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (from.isBlank() || mailSender == null) {
            log.info("[초대메일] 메일 계정 미설정, 발송 대신 링크만 기록. to={}, link={}", mail.emails(), link);
            return;
        }

        String subject = "[Devchat] " + mail.inviterNickname() + "님이 '" + mail.roomName() + "' 채팅방에 초대했어요";
        String html = buildHtml(mail, link);
        int sent = 0;
        for (String to : mail.emails()) {
            try {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
                helper.setFrom(from, "Devchat");
                helper.setTo(to);
                helper.setSubject(subject);
                helper.setText(html, true);
                mailSender.send(message);
                sent++;
            } catch (Exception e) {
                log.warn("[초대메일] 발송 실패. to={}, room={}", to, mail.roomName(), e);
            }
        }
        log.info("[초대메일] 발송 완료 {}/{}건. room={}", sent, mail.emails().size(), mail.roomName());
    }

    /** 프론트의 /invite/{초대코드} 화면. 로그인 후 방 정보를 보여주고 바로 입장시킨다. */
    String inviteLink(String inviteCode) {
        return frontendUrl + "/invite/" + UriUtils.encodePathSegment(inviteCode, StandardCharsets.UTF_8);
    }

    private String buildHtml(InviteMail mail, String link) {
        String inviter = HtmlUtils.htmlEscape(mail.inviterNickname());
        String room = HtmlUtils.htmlEscape(mail.roomName());
        String code = HtmlUtils.htmlEscape(mail.inviteCode());
        String href = HtmlUtils.htmlEscape(link);
        return """
            <div style="font-family:-apple-system,'Apple SD Gothic Neo','Malgun Gothic',sans-serif;max-width:480px;margin:0 auto;padding:32px 24px;color:#1d1d1b">
              <h2 style="margin:0 0 12px;font-size:20px">채팅방 초대가 도착했어요</h2>
              <p style="margin:0 0 24px;line-height:1.6"><b>%s</b>님이 Devchat 채팅방 <b>%s</b>에 초대했어요.</p>
              <a href="%s" style="display:inline-block;background:#2f6fde;color:#ffffff;text-decoration:none;padding:12px 22px;border-radius:8px;font-weight:600">채팅방 입장하기</a>
              <p style="margin:24px 0 0;font-size:13px;color:#6b6b66;line-height:1.6">
                버튼이 안 열리면 이 주소를 브라우저에 붙여 넣으세요.<br>%s<br><br>
                초대 코드: <code>%s</code>
              </p>
            </div>
            """.formatted(inviter, room, href, href, code);
    }
}
