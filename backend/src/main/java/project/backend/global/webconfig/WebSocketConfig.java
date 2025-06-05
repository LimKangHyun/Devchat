package project.backend.global.webconfig;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

	//클라이언트가 연결할 웹소켓 엔드포인트 지정
	//해당 주소로 접속 시 웹소켓 핸드셰이크 커넥션 생성
	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		registry.addEndpoint("/ws")
			.setAllowedOriginPatterns("http://localhost:3000")
			.withSockJS();
	}

	@Override
	public void configureMessageBroker(MessageBrokerRegistry registry) {
		registry.setApplicationDestinationPrefixes("/chat"); //클라이언트 -> 서버, 클라이언트에서 SEND 요청을 처리
		//서버 -> 클라이언트, 해당 경로를 SUBSCRIBE하는 클라이언트에게 메세지를 전달
		registry.enableSimpleBroker("/topic")
			.setHeartbeatValue(new long[]{10000, 10000}) // [서버->클라, 클라->서버] 10초마다
			.setTaskScheduler(customWebSocketTaskScheduler());
	}

	@Bean
	public ThreadPoolTaskScheduler customWebSocketTaskScheduler() {
		ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
//        scheduler.setPoolSize(1); // 너무 높지 않게 설정
//        scheduler.setThreadNamePrefix("wss-heartbeat-thread-");
		scheduler.initialize();
		return scheduler;
	}
}
