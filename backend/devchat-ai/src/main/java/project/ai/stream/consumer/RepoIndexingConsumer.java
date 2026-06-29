package project.ai.stream.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;
import project.common.message.RepoIndexingMessage;
import project.ai.service.RepoIndexingService;

@Slf4j
@Component
@RequiredArgsConstructor
public class RepoIndexingConsumer implements StreamListener<String, ObjectRecord<String, String>> {

    private final ObjectMapper objectMapper;
    private final RepoIndexingService repoIndexingService;

    @Override
    public void onMessage(ObjectRecord<String, String> record) {
        try {
            RepoIndexingMessage message = objectMapper.readValue(record.getValue(), RepoIndexingMessage.class);
            log.info("레포 인덱싱 요청 수신: roomId={}", message.roomId());
            repoIndexingService.indexRepository(message.roomId(), message.repositoryUrl(), message.memberId());
        } catch (Exception e) {
            log.error("레포 인덱싱 실패: {}", e.getMessage(), e);
        }
    }
}