package project.api.domain.aireview.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import project.api.domain.chat.chatmessage.app.ChatMessageService;
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
        try {
            GitSummaryResultMessage message = objectMapper.readValue(record.getValue(), GitSummaryResultMessage.class);
            log.info("Git 요약 결과 수신: roomId={}", message.roomId());

            chatMessageService.updateToSummary(message.messageId(), message.summarizedContent());

            messagingTemplate.convertAndSend("/topic/chat/" + message.roomId(),
                    message.summarizedContent());
        } catch (Exception e) {
            log.error("Git 요약 결과 처리 실패: {}", e.getMessage(), e);
        }
    }
}