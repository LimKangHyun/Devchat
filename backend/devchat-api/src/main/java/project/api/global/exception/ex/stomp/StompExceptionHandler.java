package project.api.global.exception.ex.stomp;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.ControllerAdvice;
import project.common.exception.ex.BaseException;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
@RequiredArgsConstructor
public class StompExceptionHandler {

    /** 클라이언트가 SEND에 붙이는 임시 메시지 id. 오류에 담아 어느 메시지가 실패했는지 알려준다 */
    public static final String CLIENT_MESSAGE_ID_HEADER = "client-message-id";

    private final SimpMessagingTemplate messagingTemplate;

    @MessageExceptionHandler(BaseException.class)
    public void handleBaseException(BaseException e, Principal principal,
                                    @Header(name = CLIENT_MESSAGE_ID_HEADER, required = false) String clientMessageId) {
        sendError(principal, e.getErrorCode().getCode(), e.getErrorCode().getMessage(), clientMessageId);
    }

    @MessageExceptionHandler(Exception.class)
    public void handleException(Exception e, Principal principal,
                                @Header(name = CLIENT_MESSAGE_ID_HEADER, required = false) String clientMessageId) {
        sendError(principal, "INTERNAL_ERROR", "오류가 발생했어요. 잠시 후 다시 시도해주세요.", clientMessageId);
    }

    private void sendError(Principal principal, String type, String message, String clientMessageId) {
        Map<String, Object> body = new HashMap<>();
        body.put("type", type);
        body.put("message", message);
        if (clientMessageId != null) body.put("clientMessageId", clientMessageId);
        messagingTemplate.convertAndSendToUser(principal.getName(), "/queue/errors", body);
    }
}
