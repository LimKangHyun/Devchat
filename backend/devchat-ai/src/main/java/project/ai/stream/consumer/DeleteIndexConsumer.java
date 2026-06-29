package project.ai.stream.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;
import project.common.message.DeleteIndexMessage;
import project.ai.client.PineconeClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeleteIndexConsumer implements StreamListener<String, ObjectRecord<String, String>> {

    private final ObjectMapper objectMapper;
    private final PineconeClient pineconeClient;

    @Override
    public void onMessage(ObjectRecord<String, String> record) {
        try {
            DeleteIndexMessage message = objectMapper.readValue(record.getValue(), DeleteIndexMessage.class);
            log.info("Pinecone 삭제 요청 수신: roomId={}", message.roomId());
            pineconeClient.deleteNamespace(String.valueOf(message.roomId()));
        } catch (Exception e) {
            log.error("Pinecone 삭제 실패: {}", e.getMessage(), e);
        }
    }
}