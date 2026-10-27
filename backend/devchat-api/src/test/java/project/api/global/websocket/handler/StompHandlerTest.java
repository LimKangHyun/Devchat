package project.api.global.websocket.handler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import project.api.auth.dto.MemberDetails;
import project.api.domain.chat.chatroom.dao.ChatParticipantRepository;

import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * SUBSCRIBE를 거절할 때 예외를 던지면 Spring이 ERROR 프레임을 보내고 웹소켓 연결 전체를 닫는다.
 * 클라이언트는 재연결 후 같은 구독을 다시 보내므로 끊김이 반복된다.
 * 그래서 거절할 구독은 그 프레임만 버리고(null) 연결은 유지해야 한다.
 */
@DisplayName("StompHandler 구독 권한 검사")
class StompHandlerTest {

    private final ChatParticipantRepository participantRepository = mock(ChatParticipantRepository.class);
    private final MessageChannel channel = mock(MessageChannel.class);
    private StompHandler handler;

    @BeforeEach
    void setUp() {
        handler = new StompHandler(participantRepository);
    }

    @Test
    @DisplayName("참여 중인 방 구독은 통과시키고 세션에 방 정보를 남긴다")
    void participantSubscribePasses() {
        given(participantRepository.existsByParticipantIdAndChatRoomIdAndIsActiveTrue(1L, 10L)).willReturn(true);
        Map<String, Object> attrs = new HashMap<>();

        Message<?> result = handler.preSend(subscribe("/topic/chat/10", user(1L), attrs), channel);

        assertThat(result).isNotNull();
        assertThat(attrs).containsEntry("currentRoomId", 10L).containsEntry("memberId", 1L);
    }

    @Test
    @DisplayName("참여하지 않은 방 구독은 연결을 끊지 않고 그 구독만 버린다")
    void nonParticipantSubscribeIsDroppedWithoutException() {
        given(participantRepository.existsByParticipantIdAndChatRoomIdAndIsActiveTrue(1L, 99L)).willReturn(false);

        assertThatCode(() -> assertThat(handler.preSend(subscribe("/topic/chat/99", user(1L), new HashMap<>()), channel)).isNull())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("인증 정보가 없거나 주소가 잘못된 구독도 예외 없이 버린다")
    void unauthenticatedOrMalformedSubscribeIsDropped() {
        assertThatCode(() -> {
            assertThat(handler.preSend(subscribe("/topic/chat/10", null, new HashMap<>()), channel)).isNull();
            assertThat(handler.preSend(subscribe("/topic/chat/abc", user(1L), new HashMap<>()), channel)).isNull();
            assertThat(handler.preSend(subscribe("/topic/chat/", user(1L), new HashMap<>()), channel)).isNull();
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("채팅방이 아닌 구독은 검사하지 않는다")
    void otherDestinationsPass() {
        assertThat(handler.preSend(subscribe("/topic/notifications/alice", user(1L), new HashMap<>()), channel)).isNotNull();
    }

    private static Principal user(Long id) {
        MemberDetails details = mock(MemberDetails.class);
        given(details.getId()).willReturn(id);
        return new UsernamePasswordAuthenticationToken(details, null, List.of());
    }

    private static Message<byte[]> subscribe(String destination, Principal user, Map<String, Object> attrs) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        accessor.setSubscriptionId("sub-0");
        accessor.setSessionId("s1");
        accessor.setUser(user);
        accessor.setSessionAttributes(attrs);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
