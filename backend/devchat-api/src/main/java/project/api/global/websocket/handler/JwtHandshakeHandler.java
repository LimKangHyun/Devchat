package project.api.global.websocket.handler;

import java.security.Principal;
import java.util.Map;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

@Component
public class JwtHandshakeHandler extends DefaultHandshakeHandler {

    @Override
    protected Principal determineUser(ServerHttpRequest request, WebSocketHandler wsHandler,
        Map<String, Object> attributes) {
        Object authObj = attributes.get("auth"); //handshakeInterceptor에서 저장해둔 인증 객체

        if (authObj instanceof Authentication auth && auth.isAuthenticated()) {
            return auth; //웹소켓 세션의 principle로 주입
        }

        return null;
    }
}