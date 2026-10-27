package project.api.global.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional Outbox 저장과 상태 변경.
 *
 * enqueue는 반드시 호출한 쪽의 트랜잭션 안에서 실행한다(MANDATORY). 그래야 "리뷰를 만들었다"와 "보낼 메시지를 적었다"가
 * 함께 커밋되거나 함께 롤백된다. 실제 전송은 OutboxRelay가 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    /** 이 횟수만큼 시도해도 실패하면 포기한다 (재시도 간격 2초부터 두 배씩, 최대 5분 → 약 8분) */
    @Value("${outbox.max-attempts:8}")
    private int maxAttempts;

    /** 한 번 가져간 행을 다른 릴레이가 다시 가져가기까지의 시간. 처리 중 서버가 죽으면 이 시간 뒤 다시 처리된다 */
    @Value("${outbox.lease:5m}")
    private Duration lease;

    private static final Duration MAX_BACKOFF = Duration.ofMinutes(5);

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(OutboxEventType type, Long aggregateId, Object payload) {
        repository.save(OutboxEvent.pending(type, aggregateId, toJson(payload), LocalDateTime.now()));
    }

    /** payload가 이미 JSON 문자열이면 그대로 저장한다 */
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueueJson(OutboxEventType type, Long aggregateId, String json) {
        repository.save(OutboxEvent.pending(type, aggregateId, json, LocalDateTime.now()));
    }

    /** 보낼 차례인 행을 가져가며 lease를 건다. 짧은 트랜잭션이라 GitHub·Redis 호출 동안 DB 연결을 붙잡지 않는다 */
    @Transactional
    public List<OutboxEvent> leaseDue(int limit) {
        LocalDateTime now = LocalDateTime.now();
        List<OutboxEvent> due = repository.lockDue(now, limit);
        due.forEach(e -> e.lease(now, lease));
        return due;
    }

    @Transactional
    public void markSent(Long id) {
        repository.findById(id).ifPresent(e -> e.markSent(LocalDateTime.now()));
    }

    /**
     * 실패를 기록한다. 아직 시도 횟수가 남았으면 백오프 뒤로 미루고, 다 썼으면 FAILED로 둔다.
     *
     * @return 포기했으면(FAILED) true
     */
    @Transactional
    public boolean recordFailure(Long id, String error) {
        OutboxEvent e = repository.findById(id).orElse(null);
        if (e == null) return false;
        if (e.getAttempts() >= maxAttempts) {
            e.markFailed(error);
            return true;
        }
        e.scheduleRetry(LocalDateTime.now().plus(backoff(e.getAttempts())), error);
        return false;
    }

    /** 1번째 실패 2초, 2번째 4초 … 최대 5분 */
    static Duration backoff(int attempts) {
        long seconds = 1L << Math.min(Math.max(attempts, 1), 16);
        return seconds >= MAX_BACKOFF.toSeconds() ? MAX_BACKOFF : Duration.ofSeconds(seconds);
    }

    @Transactional
    public int deleteSentBefore(LocalDateTime before) {
        return repository.deleteSentBefore(before);
    }

    public <T> T readPayload(OutboxEvent event, Class<T> type) {
        try {
            return objectMapper.readValue(event.getPayload(), type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("outbox payload 역직렬화 실패. id=" + event.getId(), e);
        }
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("outbox payload 직렬화 실패", e);
        }
    }
}
