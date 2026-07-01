package project.api.global.websocket.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.WebSocketMessageBrokerStats;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import project.api.global.websocket.handler.JwtHandshakeHandler;
import project.api.global.websocket.handler.StompHandler;
import project.api.global.websocket.interceptor.RateLimitChannelInterceptor;
import project.api.global.websocket.interceptor.WebSocketHandShakeInterceptor;

@Configuration
@EnableWebSocketMessageBroker
@Slf4j
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketHandShakeInterceptor handShakeInterceptor;
    private final JwtHandshakeHandler jwtHandshakeHandler;
    private final StompHandler stompHandler;
    private final RateLimitChannelInterceptor rateLimitChannelInterceptor;

    @Value("${url.domain-url}")
    private String domainUrl;

    //클라이언트가 연결할 웹소켓 엔드포인트 지정
    //해당 주소로 접속 시 웹소켓 핸드셰이크 커넥션 생성
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
            .setHandshakeHandler(jwtHandshakeHandler)
            .setAllowedOriginPatterns(domainUrl)
            .addInterceptors(handShakeInterceptor);
    }

    @Override
    public void configureClientInboundChannel(
        ChannelRegistration registration) {

        registration.interceptors(stompHandler, rateLimitChannelInterceptor);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/chat",
            "/dm"); //클라이언트 -> 서버, 클라이언트에서 SEND 요청을 처리
        //서버 -> 클라이언트, 해당 경로를 SUBSCRIBE하는 클라이언트에게 메세지를 전달
        registry.enableSimpleBroker("/topic", "/queue")
            .setHeartbeatValue(new long[]{10000, 20000})
            .setTaskScheduler(customWebSocketTaskScheduler());
        registry.setUserDestinationPrefix("/user");
    }

    @Bean
    public ThreadPoolTaskScheduler customWebSocketTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
//        scheduler.setPoolSize(1); // 너무 높지 않게 설정
//        scheduler.setThreadNamePrefix("wss-heartbeat-thread-");
        scheduler.initialize();
        return scheduler;
    }

    // 로그용
    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.addDecoratorFactory(handler -> new WebSocketHandlerDecorator(handler) {

            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                log.info("✅ WebSocket connected! - sessionId={}, principal={}",
                    session.getId(),
                    session.getPrincipal() != null ? session.getPrincipal().getName()
                        : "anonymous");

                super.afterConnectionEstablished(session);
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus)
                throws Exception {
                log.warn("🔌 WebSocket closed! - sessionId={}, reason={}", session.getId(),
                    closeStatus);

                super.afterConnectionClosed(session, closeStatus);
            }
        });
    }

    @Bean
    public WebSocketMessageBrokerStats brokerStats(WebSocketMessageBrokerStats stats) {
        stats.setLoggingPeriod(60 * 1000); // 1분마다 상태 로그 출력
        return stats;
    }
}
