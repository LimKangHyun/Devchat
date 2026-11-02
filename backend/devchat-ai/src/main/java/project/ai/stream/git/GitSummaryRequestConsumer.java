package project.ai.stream.git;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;
import project.common.message.git.GitSummaryRequestMessage;
import project.ai.client.GeminiClient;

/**
 * Git 이벤트 요약 요청. AckOnSuccessListener로 감싸 등록해, 결과를 발행하면 ACK되고
 * 발행이 실패하면(Redis 장애) PEL에 남아 재처리된다.
 *
 * 요약 실패(Gemini 오류·사용량 한도)는 원본을 그대로 결과로 보내므로 재처리 대상이 아니다.
 * 재처리로 결과가 두 번 발행되어도 괜찮다: API 서버(GitSummaryResultConsumer)는 messageId의 메시지 내용을
 * 요약으로 덮어쓰고 채팅방에 다시 보낼 뿐이라, 같은 메시지가 같은(또는 다시 요약한) 내용으로 한 번 더 갱신된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GitSummaryRequestConsumer implements StreamListener<String, ObjectRecord<String, String>> {

    public static final String STREAM_KEY = "stream:git-summary:request";
    public static final String GROUP_NAME = "ai-git-summary-group";

    private final ObjectMapper objectMapper;
    private final GeminiClient geminiClient;
    private final GitSummaryResultProducer gitSummaryResultProducer;

    @Override
    public void onMessage(ObjectRecord<String, String> record) {
        GitSummaryRequestMessage message;
        try {
            message = objectMapper.readValue(record.getValue(), GitSummaryRequestMessage.class);
        } catch (Exception e) {
            log.error("Git 요약 요청 역직렬화 실패, 메시지 폐기 - recordId={}, raw={}", record.getId(), record.getValue(), e);
            return;
        }
        log.info("Git 요약 요청 수신: roomId={}, eventType={}", message.roomId(), message.eventType());

        String summarized = geminiClient.summarizeGitEvent(
            message.roomId(), message.eventType(), message.prStatus(), message.fullContent());

        gitSummaryResultProducer.publish(message.roomId(), message.messageId(), summarized);
    }
}
