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

/**
 * 채팅방 삭제에 따른 벡터 인덱스 삭제. AckOnSuccessListener로 감싸 등록해, 정상 리턴하면 ACK되고
 * 예외를 던지면 PEL에 남아 재처리된다.
 *
 * 다시 처리해도 안전하다: 취소 플래그를 다시 걸고, 이미 지운 방의 청크를 다시 지울 뿐이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeleteIndexConsumer implements StreamListener<String, ObjectRecord<String, String>> {

    public static final String STREAM_KEY = "stream:delete-index:request";
    public static final String GROUP_NAME = "ai-delete-index-group";

    private final ObjectMapper objectMapper;
    private final QdrantClient qdrantClient;
    private final RepoIndexingService repoIndexingService;

    @Override
    public void onMessage(ObjectRecord<String, String> record) {
        DeleteIndexMessage message;
        try {
            message = objectMapper.readValue(record.getValue(), DeleteIndexMessage.class);
        } catch (Exception e) {
            log.error("벡터 인덱스 삭제 요청 역직렬화 실패, 메시지 폐기 - recordId={}, raw={}", record.getId(), record.getValue(), e);
            return;
        }
        log.info("벡터 인덱스 삭제 요청 수신: roomId={}", message.roomId());
        // 채팅방이 삭제될 때 인덱싱이 돌고 있으면, 여기서 지운 뒤에도 남은 배치가 저장된다.
        // 그래서 먼저 취소를 걸어 둔다. 인덱싱은 남은 배치가 끝난 뒤 청크를 한 번 더 지우고 멈춘다.
        // 아직 시작하지 않은(스트림에 남은) 인덱싱 요청도 이 플래그를 보고 건너뛴다(RepoIndexConsumer).
        repoIndexingService.cancelIndexing(message.roomId());
        // 실패하면 예외가 그대로 나가 ACK되지 않고 나중에 다시 시도한다
        qdrantClient.deleteRepo(String.valueOf(message.roomId()));
    }
}
