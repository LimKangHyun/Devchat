package project.backend.global.security.interceptor;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import project.backend.auth.dto.MemberDetails;
import project.backend.domain.chat.chatroom.dao.ChatParticipantRepository;
import project.backend.global.exception.errorcode.ChatRoomErrorCode;
import project.backend.global.exception.ex.ChatRoomException;

@Component
@RequiredArgsConstructor
public class StompHandler implements ChannelInterceptor {

    private final ChatParticipantRepository chatParticipantRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {

        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {

            String destination = accessor.getDestination();

            if (destination == null || !destination.startsWith("/topic/chat/")) {
                return message;
            }

            Long roomId = extractRoomId(destination);

            Authentication authentication = (Authentication) accessor.getUser();

            if (authentication == null || !authentication.isAuthenticated()) {
                return message;
            }

            MemberDetails user = (MemberDetails) authentication.getPrincipal();
            Long memberId = user.getId();

            boolean exists =
                chatParticipantRepository
                    .existsByParticipantIdAndChatRoomIdAndIsActiveTrue(memberId, roomId);

            if (!exists) {
                throw new ChatRoomException(ChatRoomErrorCode.NOT_PARTICIPANT);
            }
        }

        return message;
    }

    private Long extractRoomId(String destination) {

        String[] parts = destination.split("/");

        if (parts.length < 4) {
            throw new IllegalArgumentException("Invalid destination: " + destination);
        }

        return Long.parseLong(parts[3]);
    }
}