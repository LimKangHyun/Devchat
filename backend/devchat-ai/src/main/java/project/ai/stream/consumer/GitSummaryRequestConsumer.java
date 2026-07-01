package project.ai.stream.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;
import project.common.message.GitSummaryRequestMessage;
import project.ai.client.GeminiClient;
import project.ai.stream.producer.GitSummaryResultProducer;

@Slf4j
@Component
@RequiredArgsConstructor
public class GitSummaryRequestConsumer implements StreamListener<String, ObjectRecord<String, String>> {

    private final ObjectMapper objectMapper;
    private final GeminiClient geminiClient;
    private final GitSummaryResultProducer gitSummaryResultProducer;

    @Override
    public void onMessage(ObjectRecord<String, String> record) {
        try {
            GitSummaryRequestMessage message = objectMapper.readValue(record.getValue(), GitSummaryRequestMessage.class);
            log.info("Git 요약 요청 수신: roomId={}, eventType={}", message.roomId(), message.eventType());

            String summarized = geminiClient.summarizeGitEvent(
                message.eventType(), message.prStatus(), message.fullContent());

            gitSummaryResultProducer.publish(message.roomId(), summarized);
        } catch (Exception e) {
            log.error("Git 요약 처리 실패: {}", e.getMessage(), e);
        }
    }
}