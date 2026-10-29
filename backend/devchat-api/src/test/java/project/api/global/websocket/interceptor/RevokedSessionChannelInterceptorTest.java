package project.api.global.websocket.interceptor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import project.api.auth.app.RevokedSessionStore;
import project.api.auth.dto.MemberDetails;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class RevokedSessionChannelInterceptorTest {

    @Mock RevokedSessionStore revokedSessionStore;
    @InjectMocks RevokedSessionChannelInterceptor interceptor;

    private final MessageChannel channel = mock(MessageChannel.class);

    private Message<byte[]> frame(StompCommand command, String sessionId) {
        MemberDetails member = new MemberDetails(1L, "user1", "닉네임", "img.png", sessionId);
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setUser(new UsernamePasswordAuthenticationToken(member, null, member.getAuthorities()));
        accessor.setDestination("/chat/room/1");
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    void revokedSession_sendDropped() {
        given(revokedSessionStore.isRevoked("s-1")).willReturn(true);

        assertThat(interceptor.preSend(frame(StompCommand.SEND, "s-1"), channel)).isNull();
    }

    @Test
    void revokedSession_subscribeDropped() {
        given(revokedSessionStore.isRevoked("s-1")).willReturn(true);

        assertThat(interceptor.preSend(frame(StompCommand.SUBSCRIBE, "s-1"), channel)).isNull();
    }

    @Test
    void revokedSession_disconnectPasses() {
        Message<byte[]> message = frame(StompCommand.DISCONNECT, "s-1");

        assertThat(interceptor.preSend(message, channel)).isSameAs(message);
    }

    @Test
    void activeSession_passes() {
        given(revokedSessionStore.isRevoked("s-1")).willReturn(false);
        Message<byte[]> message = frame(StompCommand.SEND, "s-1");

        assertThat(interceptor.preSend(message, channel)).isSameAs(message);
    }

    @Test
    void noSid_passes() {
        Message<byte[]> message = frame(StompCommand.SEND, null);

        assertThat(interceptor.preSend(message, channel)).isSameAs(message);
    }
}
