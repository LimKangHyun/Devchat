package project.api.domain.chat.typing;

import java.security.Principal;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import project.api.auth.dto.MemberDetails;

/** 입력 중 신호: SEND /chat/typing/{roomId} {"typing": true|false} → /topic/chat/{roomId}/typing */
@Controller
@RequiredArgsConstructor
public class TypingController {

    public static final String DESTINATION_PREFIX = "/typing/";

    private final TypingService typingService;

    public record TypingRequest(boolean typing) {}

    @MessageMapping(DESTINATION_PREFIX + "{roomId}")
    public void typing(@DestinationVariable Long roomId, @Payload TypingRequest request, Principal principal) {
        if (!(principal instanceof Authentication auth) || !(auth.getPrincipal() instanceof MemberDetails user)) {
            return;
        }
        typingService.signal(user.getId(), user.getNickname(), roomId, request.typing());
    }
}
