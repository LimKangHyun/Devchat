package project.api.domain.aireview.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import project.api.domain.chat.chatmessage.app.ChatMessageService;
import project.api.domain.chat.chatmessage.dto.ChatMessageResponse;
import project.common.message.git.GitSummaryResultMessage;

@Slf4j
@Component
@RequiredArgsConstructor
public class GitSummaryResultConsumer implements StreamListener<String, ObjectRecord<String, String>> {

    private final ChatMessageService chatMessageService;

    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void onMessage(ObjectRecord<String, String> record) {
        // 역직렬화 실패는 재시도해도 계속 실패하므로(포이즌 메시지) 로그만 남기고 정상 리턴해 ACK되게 한다.
        GitSummaryResultMessage message;
        try {
            message = objectMapper.readValue(record.getValue(), GitSummaryResultMessage.class);
        } catch (Exception e) {
            log.error("Git 요약 결과 역직렬화 실패, 메시지 폐기: raw={}", record.getValue(), e);
            return;
        }
        log.info("Git 요약 결과 수신: roomId={}", message.roomId());

        // 처리 실패(DB 일시 장애 등)는 예외를 그대로 던져 ACK를 막고 재처리되게 한다.
        // 요약 내용으로 덮어쓰는 작업이라 다시 처리해도 결과가 같다.
        ChatMessageResponse response = chatMessageService.updateToSummary(message.messageId(), message.summarizedContent());
        messagingTemplate.convertAndSend("/topic/chat/" + message.roomId(), response);
    }
}