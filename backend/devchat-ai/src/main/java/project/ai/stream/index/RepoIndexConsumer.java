package project.ai.stream.index;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;
import project.common.message.index.IndexingKeys;
import project.common.message.index.RepoIndexRequestMessage;
import project.ai.indexing.RepoIndexingService;

/**
 * 레포 인덱싱 요청. AckOnSuccessListener로 감싸 등록해, 정상 리턴하면 ACK되고 예외를 던지면 PEL에 남아 재처리된다.
 *
 * ACK 시점: 인덱싱을 비동기 실행기(repoIndexingExecutor)에 넘기면 바로 ACK한다. 인덱싱이 끝날 때까지 기다리지 않는다.
 * 인덱싱은 몇 분씩 걸려서, 끝날 때 ACK하면 재처리 기준 시간(idle)을 그보다 길게 잡아야 하고, 그래도 넘기면
 * 돌고 있는 인덱싱을 다시 돌리게 된다. 넘긴 뒤의 실패는 이미 다른 장치가 맡는다.
 * - 실패·취소: 인덱싱이 결과 스트림(stream:repo-index:result)으로 알린다
 * - 서버가 죽음: 인덱싱 락 연장이 멈춰 2분 안에 락이 사라지고, API 서버가 RUNNING을 FAILED로 정리한다
 * 그래서 재처리는 "실행기에 넘기기 전에 잃어버린 요청"(응답 유실, 실행기 거절 등)만 다시 넘긴다.
 *
 * 건너뛰고 ACK하는 경우(다시 처리해도 같은 방을 두 번 인덱싱하지 않게):
 * 1. 이미 인덱싱 중(락이 있음): 지금 실행이 맡고 있다
 * 2. 취소 플래그가 있음: 요청 뒤에 방장이 취소했거나 채팅방이 삭제됐다(DeleteIndexConsumer가 건다).
 *    API 서버는 새 인덱싱을 요청할 때 취소 플래그를 지우므로, 남아 있는 플래그는 이 요청 뒤에 걸린 것이다.
 *    "취소됨"을 알려 API 서버의 RUNNING을 정리한다(삭제된 방이면 API 서버가 무시한다).
 * 3. 재처리인데 요청 표시(requestedKey)가 없음: 그 요청은 이미 끝났거나(인덱싱이 끝나며 지운다) 만료됐다(10분).
 *    API 서버도 이 표시가 없으면 RUNNING을 비정상 종료로 보고 FAILED로 정리하므로 다시 돌리지 않는다.
 *    처음 전달된 요청에는 이 검사를 하지 않는다. AI 서버가 오래 내려가 있다 돌아와 밀린 요청을 처리하는 경우
 *    (API 서버가 FAILED로 정리했어도 끝나면 COMPLETED로 바꾼다)를 지금처럼 살린다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RepoIndexConsumer implements StreamListener<String, ObjectRecord<String, String>> {

    public static final String STREAM_KEY = "stream:repo-index:request";
    public static final String GROUP_NAME = "ai-repo-index-group";

    private final ObjectMapper objectMapper;
    private final RepoIndexingService repoIndexingService;
    private final StringRedisTemplate redisTemplate;
    private final RepoIndexResultProducer repoIndexResultProducer;

    @Override
    public void onMessage(ObjectRecord<String, String> record) {
        handle(record, false);
    }

    /** PEL에서 다시 가져온 메시지. PendingMessageReclaimer가 부른다. */
    public void onReclaimed(ObjectRecord<String, String> record) {
        handle(record, true);
    }

    private void handle(ObjectRecord<String, String> record, boolean redelivered) {
        RepoIndexRequestMessage message;
        try {
            message = objectMapper.readValue(record.getValue(), RepoIndexRequestMessage.class);
        } catch (Exception e) {
            log.error("레포 인덱싱 요청 역직렬화 실패, 메시지 폐기 - recordId={}, raw={}", record.getId(), record.getValue(), e);
            return;
        }
        Long roomId = message.roomId();
        log.info("레포 인덱싱 요청 수신: roomId={}, recordId={}, 재처리={}", roomId, record.getId(), redelivered);

        if (hasKey(IndexingKeys.lockKey(roomId))) {
            log.info("이미 인덱싱 중인 레포, 요청 건너뜀. roomId={}", roomId);
            return;
        }
        if (hasKey(IndexingKeys.cancelKey(roomId))) {
            log.info("취소(또는 채팅방 삭제)된 인덱싱 요청, 건너뜀. roomId={}", roomId);
            repoIndexResultProducer.publishCancelled(roomId);
            redisTemplate.delete(IndexingKeys.requestedKey(roomId));
            return;
        }
        if (redelivered && !hasKey(IndexingKeys.requestedKey(roomId))) {
            log.info("이미 끝났거나 만료된 인덱싱 요청(요청 표시 없음), 다시 처리하지 않음. roomId={}, recordId={}",
                roomId, record.getId());
            return;
        }

        // @Async: 실행기에 넘기고 바로 돌아온다. 넘기지 못하면(실행기 거절 등) 예외가 나가 ACK되지 않는다
        repoIndexingService.indexRepository(roomId, message.repositoryUrl(), message.memberId());
    }

    private boolean hasKey(String key) {
        // Redis 오류는 그대로 던진다. 확인하지 못한 채 인덱싱하지 않고 나중에 다시 시도한다
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
}
