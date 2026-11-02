package project.ai.stream.index;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;
import project.ai.indexing.RepoIndexingService;
import project.common.message.index.FileReindexMessage;

/**
 * 파일 재인덱싱 요청. AckOnSuccessListener로 감싸 등록해, 정상 리턴하면 ACK되고 예외를 던지면 PEL에 남아 재처리된다.
 *
 * 다시 처리해도 결과가 같다: 청크 ID가 (방, 파일, 순번)으로 정해져 같은 내용은 덮어쓰고, 이번에 덮어쓰지 않은 옛 청크만 지운다.
 * 삭제(removed)도 이미 지운 파일을 다시 지우는 것이라 안전하다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileReindexConsumer implements StreamListener<String, ObjectRecord<String, String>> {

    public static final String STREAM_KEY = "stream:file-reindex:request";
    public static final String GROUP_NAME = "ai-file-reindex-group";

    private final ObjectMapper objectMapper;
    private final RepoIndexingService repoIndexingService;

    @Override
    public void onMessage(ObjectRecord<String, String> record) {
        FileReindexMessage message;
        try {
            message = objectMapper.readValue(record.getValue(), FileReindexMessage.class);
        } catch (Exception e) {
            // 다시 시도해도 계속 실패하는 메시지라 버린다(정상 리턴 → ACK)
            log.error("파일 재인덱싱 요청 역직렬화 실패, 메시지 폐기 - recordId={}, raw={}", record.getId(), record.getValue(), e);
            return;
        }
        log.info("파일 재인덱싱 요청 수신: roomId={}, filePath={}, status={}",
                message.roomId(), message.filePath(), message.status());
        // 실패(임베딩 키 소진, Qdrant 장애 등)하면 예외가 그대로 나가 ACK되지 않고 나중에 다시 시도한다
        repoIndexingService.reindexFile(message);
    }
}
