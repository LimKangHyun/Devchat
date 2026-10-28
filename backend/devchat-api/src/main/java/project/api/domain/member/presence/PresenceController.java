package project.api.domain.member.presence;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

/** 브라우저가 화면이 보이는지 알린다: SEND /chat/presence/visibility {"visible": true} */
@Controller
@RequiredArgsConstructor
public class PresenceController {

    public static final String VISIBILITY_DESTINATION = "/presence/visibility";

    private final PresenceService presenceService;

    public record VisibilityRequest(boolean visible) {}

    @MessageMapping(VISIBILITY_DESTINATION)
    public void visibility(@Payload VisibilityRequest request,
                           @Header("simpSessionId") String sessionId) {
        presenceService.updateVisibility(sessionId, request.visible());
    }
}
