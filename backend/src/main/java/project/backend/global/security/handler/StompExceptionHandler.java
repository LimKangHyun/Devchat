package project.backend.global.security.handler;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.ControllerAdvice;
import project.backend.global.exception.ex.BaseException;

import java.security.Principal;
import java.util.Map;

@ControllerAdvice
@RequiredArgsConstructor
public class StompExceptionHandler {

    private final SimpMessagingTemplate messagingTemplate;

    @MessageExceptionHandler(BaseException.class)
    public void handleBaseException(BaseException e, Principal principal) {
        messagingTemplate.convertAndSendToUser(
            principal.getName(),
            "/queue/errors",
            Map.of(
                "type", e.getErrorCode().getCode(),
                "message", e.getErrorCode().getMessage()
            )
        );
    }

    @MessageExceptionHandler(Exception.class)
    public void handleException(Exception e, Principal principal) {
        messagingTemplate.convertAndSendToUser(
            principal.getName(),
            "/queue/errors",
            Map.of(
                "type", "INTERNAL_ERROR",
                "message", "오류가 발생했어요. 잠시 후 다시 시도해주세요."
            )
        );
    }
}