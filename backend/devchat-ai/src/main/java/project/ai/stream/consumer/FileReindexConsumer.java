package project.ai.stream.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;
import project.ai.service.RepoIndexingService;
import project.common.message.FileReindexMessage;

@Slf4j
@Component
@RequiredArgsConstructor
public class FileReindexConsumer implements StreamListener<String, ObjectRecord<String, String>> {

    private final ObjectMapper objectMapper;
    private final RepoIndexingService repoIndexingService;

    @Override
    public void onMessage(ObjectRecord<String, String> record) {
        try {
            FileReindexMessage message = objectMapper.readValue(record.getValue(), FileReindexMessage.class);
            log.info("파일 재인덱싱 요청 수신: roomId={}, filePath={}, status={}",
                    message.roomId(), message.filePath(), message.status());
            repoIndexingService.reindexFile(message);
        } catch (Exception e) {
            log.error("파일 재인덱싱 실패: {}", e.getMessage(), e);
        }
    }
}