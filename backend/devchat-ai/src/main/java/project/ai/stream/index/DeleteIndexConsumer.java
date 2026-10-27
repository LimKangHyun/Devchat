package project.ai.stream.index;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;
import project.common.message.index.DeleteIndexMessage;
import project.ai.client.QdrantClient;
import project.ai.indexing.RepoIndexingService;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeleteIndexConsumer implements StreamListener<String, ObjectRecord<String, String>> {

    private final ObjectMapper objectMapper;
    private final QdrantClient qdrantClient;
    private final RepoIndexingService repoIndexingService;

    @Override
    public void onMessage(ObjectRecord<String, String> record) {
        try {
            DeleteIndexMessage message = objectMapper.readValue(record.getValue(), DeleteIndexMessage.class);
            log.info("벡터 인덱스 삭제 요청 수신: roomId={}", message.roomId());
            // 채팅방이 삭제될 때 인덱싱이 돌고 있으면, 여기서 지운 뒤에도 남은 배치가 저장된다.
            // 그래서 먼저 취소를 걸어 둔다. 인덱싱은 남은 배치가 끝난 뒤 청크를 한 번 더 지우고 멈춘다.
            repoIndexingService.cancelIndexing(message.roomId());
            qdrantClient.deleteRepo(String.valueOf(message.roomId()));
        } catch (Exception e) {
            log.error("벡터 인덱스 삭제 실패: {}", e.getMessage(), e);
        }
    }
}