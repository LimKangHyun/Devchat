package project.backend.global.security.interceptor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketChannelInterceptor implements ChannelInterceptor {

	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {

		// 웹소켓을 통해 들어온 메시지를 STOMP 헤더 기반으로 래핑
		StompHeaderAccessor accessor = MessageHeaderAccessor
			.getAccessor(message, StompHeaderAccessor.class);

		if (StompCommand.CONNECT.equals(accessor.getCommand())) {
			// handshakeInterceptor에서 넣어준 auth 객체를 꺼내기
			Authentication auth = (Authentication) accessor.getSessionAttributes().get("auth");

			if (auth != null) {
				accessor.setUser(auth); //stomp 메시지의 principal로 주입
			}
		}

		// 메타정보(헤더) 반영된 새 메시지로 return
		return MessageBuilder.createMessage(message.getPayload(), accessor.getMessageHeaders());
	}
}
