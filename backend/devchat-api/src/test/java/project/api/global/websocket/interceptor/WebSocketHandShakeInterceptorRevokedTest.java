package project.api.global.websocket.interceptor;

import jakarta.servlet.http.Cookie;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.WebSocketHandler;
import project.api.auth.app.RevokedSessionStore;
import project.api.auth.dto.MemberDetails;
import project.api.auth.jwt.JwtProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class WebSocketHandShakeInterceptorRevokedTest {

    @Mock RevokedSessionStore revokedSessionStore;
    @Mock WebSocketHandler wsHandler;

    WebSocketHandShakeInterceptor interceptor;
    ServletServerHttpRequest request;
    ServletServerHttpResponse response;
    Map<String, Object> attributes;

    @BeforeEach
    void setUp() {
        JwtProvider jwtProvider = new JwtProvider();
        ReflectionTestUtils.setField(jwtProvider, "SECRET_KEY", "test-secret-key");
        interceptor = new WebSocketHandShakeInterceptor(jwtProvider, revokedSessionStore);

        MemberDetails member = new MemberDetails(1L, "user1", "닉네임", "img.png", "s-1");
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/ws");
        servletRequest.setCookies(new Cookie("accessToken",
                jwtProvider.generateTokenPair(member, "s-1").accessToken()));
        request = new ServletServerHttpRequest(servletRequest);
        response = new ServletServerHttpResponse(new MockHttpServletResponse());
        attributes = new HashMap<>();
    }

    @Test
    void revokedSession_handshakeRejected() throws Exception {
        given(revokedSessionStore.isRevoked("s-1")).willReturn(true);

        assertThat(interceptor.beforeHandshake(request, response, wsHandler, attributes)).isFalse();
        assertThat(attributes).doesNotContainKey("auth");
    }

    @Test
    void activeSession_handshakeAllowed() throws Exception {
        given(revokedSessionStore.isRevoked("s-1")).willReturn(false);

        assertThat(interceptor.beforeHandshake(request, response, wsHandler, attributes)).isTrue();
        assertThat(attributes).containsKey("auth");
    }
}
