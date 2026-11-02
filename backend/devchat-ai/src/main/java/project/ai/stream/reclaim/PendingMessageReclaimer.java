package project.ai.stream.reclaim;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.PendingMessagesSummary;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;

/**
 * 스트림 하나의 PEL(전달됐지만 ACK 안 된 메시지)을 정리한다.
 *
 * 구독은 새 메시지(>)만 읽으므로, 처리하다 실패했거나 응답을 잃어버린 메시지(2026-11 Redis 시간 초과 장애)는
 * PEL에 남은 채 아무도 다시 읽지 않는다. 그래서 주기마다
 * 1. 마지막 전달 뒤 idleThreshold가 지난 메시지를 찾아
 * 2. 전달 횟수가 maxDeliveries 미만이면 XCLAIM으로 가져와 리스너에 다시 넘기고(리스너가 성공하면 ACK한다),
 * 3. maxDeliveries에 이르렀으면 더 시도하지 않고 데드레터 처리기에 넘긴 뒤 ACK한다.
 *
 * XCLAIM에 같은 idleThreshold를 걸어, 그사이 다른 쪽이 가져가거나 다시 전달된 메시지는 건드리지 않는다.
 * idleThreshold는 그 스트림의 메시지 하나를 처리하는 데 걸리는 시간보다 길어야 한다. 짧으면 처리 중인 메시지를
 * 다시 가져와 두 번 처리한다.
 *
 * AI 리뷰 스트림은 Heartbeat로 "오래 걸리는 작업"을 구분해야 해서 AiReviewPelReclaimScheduler가 따로 맡는다.
 */
@Slf4j
public class PendingMessageReclaimer {

    static final int BATCH_SIZE = 100;
    /** 한 주기에 다시 처리(또는 데드레터 처리)하는 최대 수. 오래 걸리는 메시지가 쌓여도 한 주기가 끝없이 늘지 않게 한다 */
    static final int MAX_HANDLED_PER_CYCLE = 10;
    /** 시작할 때 정리하는 PEL 상한(배치 × 횟수). 이보다 많으면 다음 시작 때 이어서 정리한다 */
    private static final int MAX_LEGACY_BATCHES = 100;
    /** 예전 PEL 정리를 마친 스트림 표시. 한 번 정리한 뒤로는 다시 하지 않는다 */
    static final String LEGACY_DONE_KEY_PREFIX = "devchat:stream:legacy-pel-cleaned:";
    private static final int LOG_PAYLOAD_PREFIX = 200;
    /** cleanUpLegacy가 이미 정리한 스트림이라 건너뛰었을 때 돌려주는 값 */
    public static final int LEGACY_ALREADY_DONE = -1;

    private final StringRedisTemplate redis;
    private final StreamReclaimMetrics metrics;
    private final String streamKey;
    private final String group;
    private final String claimer;
    private final StreamListener<String, ObjectRecord<String, String>> listener;
    private final Duration idleThreshold;
    private final long maxDeliveries;
    private final DeadLetterHandler deadLetterHandler;

    /**
     * @param claimer   가져온 메시지를 맡을 컨슈머 이름
     * @param listener  다시 처리할 리스너. 성공하면 스스로 ACK하고, 다시 시도해야 하면 예외를 던진다(AckOnSuccessListener)
     * @param maxDeliveries 최대 전달 횟수. 처음 전달을 포함해 이 횟수만큼 시도하고, 그래도 남아 있으면 데드레터로 보낸다
     */
    public PendingMessageReclaimer(StringRedisTemplate redis, StreamReclaimMetrics metrics,
                                   String streamKey, String group, String claimer,
                                   StreamListener<String, ObjectRecord<String, String>> listener,
                                   Duration idleThreshold, long maxDeliveries, DeadLetterHandler deadLetterHandler) {
        this.redis = redis;
        this.metrics = metrics;
        this.streamKey = streamKey;
        this.group = group;
        this.claimer = claimer;
        this.listener = listener;
        this.idleThreshold = idleThreshold;
        this.maxDeliveries = maxDeliveries;
        this.deadLetterHandler = deadLetterHandler;
        metrics.register(streamKey);
    }

    public String streamKey() {
        return streamKey;
    }

    /**
     * 만들어진 지 cutoff가 지난 PEL 메시지를 다시 처리하지 않고 ACK만 한다. 정리한 수를 돌려준다.
     *
     * 예전 컨슈머는 ACK를 하지 않아, PEL에 이미 처리한 메시지와 잃어버린 메시지가 섞여 있다. 오래된 것은 구분할 수
     * 없고 다시 돌리면 처리한 요청까지 다시 처리하므로 버린다. cutoff보다 새것은 평소 재처리로 넘긴다.
     * 나이는 마지막 전달 시각이 아니라 메시지 ID의 생성 시각으로 본다(XCLAIM 때마다 전달 시각이 바뀐다).
     *
     * 한 번 다 정리하면 Redis에 표시(LEGACY_DONE_KEY_PREFIX + 스트림)를 남기고, 그 뒤 시작할 때는 건너뛴다
     * (LEGACY_ALREADY_DONE을 돌려준다). 새 코드에서 cutoff보다 오래 PEL에 머무는 메시지는 재시도 중인 메시지라,
     * 매번 정리하면 재처리·데드레터 없이 조용히 버리게 된다. redis-stream이 데이터 없이 다시 뜨면 표시도 사라지지만
     * 그때는 PEL도 비어 있다.
     */
    public int cleanUpLegacy(Duration cutoff, long nowMillis) {
        String doneKey = LEGACY_DONE_KEY_PREFIX + streamKey;
        if (Boolean.TRUE.equals(redis.hasKey(doneKey))) return LEGACY_ALREADY_DONE;

        long cutoffMillis = nowMillis - cutoff.toMillis();
        int acked = 0;
        boolean complete = false;
        for (int i = 0; i < MAX_LEGACY_BATCHES; i++) {
            PendingMessages pending = redis.opsForStream().pending(streamKey, group, Range.unbounded(), BATCH_SIZE);
            List<RecordId> old = new ArrayList<>();
            for (PendingMessage p : pending) {
                if (p.getId().getTimestamp() < cutoffMillis) old.add(p.getId());
            }
            if (old.isEmpty()) {
                complete = true;
                break;
            }
            Long count = redis.opsForStream().acknowledge(streamKey, group, old.toArray(new RecordId[0]));
            acked += count == null ? 0 : count.intValue();
            // PEL은 ID 순이라, 배치 안에 새 메시지가 섞여 있으면 그 뒤로는 모두 새것이다
            if (old.size() < pending.size()) {
                complete = true;
                break;
            }
        }
        if (complete) {
            redis.opsForValue().set(doneKey, String.valueOf(nowMillis));
        }
        return acked;
    }

    /** 한 주기. 실패해도 다음 주기에 다시 하므로 예외를 밖으로 내보내지 않는다. */
    public void reclaimOnce() {
        try {
            PendingMessages pending = redis.opsForStream().pending(streamKey, group, Range.unbounded(), BATCH_SIZE);
            int handled = 0;
            for (PendingMessage p : pending) {
                if (p.getElapsedTimeSinceLastDelivery().compareTo(idleThreshold) < 0) continue;
                if (handled++ >= MAX_HANDLED_PER_CYCLE) break; // 나머지는 다음 주기에
                if (p.getTotalDeliveryCount() >= maxDeliveries) {
                    deadLetter(p);
                } else {
                    reclaim(p);
                }
            }
        } catch (Exception e) {
            log.error("PEL 재처리 실패 - stream={}", streamKey, e);
        }
    }

    /** ACK 안 된 메시지 수. 지표로 내보낸다. */
    public long refreshPendingCount() {
        PendingMessagesSummary summary = redis.opsForStream().pending(streamKey, group);
        long count = summary == null ? 0 : summary.getTotalPendingMessages();
        metrics.pending(streamKey, count);
        return count;
    }

    private void reclaim(PendingMessage p) {
        ObjectRecord<String, String> record = claim(p.getId());
        if (record == null) return; // 그사이 다른 쪽이 가져갔거나 ACK됨

        log.warn("멈춘 스트림 메시지 재처리 - stream={}, recordId={}, 전달 횟수={}",
            streamKey, p.getId(), p.getTotalDeliveryCount() + 1);
        metrics.reclaimed(streamKey);
        try {
            listener.onMessage(record);
        } catch (Exception e) {
            // ACK되지 않았으므로 PEL에 남아 다음 주기(idleThreshold 뒤)에 다시 시도하거나 데드레터로 간다
            log.warn("재처리 실패, 나중에 다시 시도 - stream={}, recordId={}", streamKey, p.getId(), e);
        }
    }

    private void deadLetter(PendingMessage p) {
        ObjectRecord<String, String> record = claim(p.getId());
        if (record == null) return;

        String payload = record.getValue();
        // 내용 전체는 데드레터 기록(API 서버 stream_dead_letter)과 스트림에 남는다. 로그에는 앞부분만 남긴다
        log.error("최대 전달 횟수({}) 초과, 데드레터로 보냄 - stream={}, recordId={}, payloadSize={}, payloadPrefix={}",
            maxDeliveries, streamKey, p.getId(), payload.length(), prefix(payload));
        try {
            deadLetterHandler.handle(streamKey, p.getIdAsString(), payload);
        } catch (Exception e) {
            // ACK하지 않는다. PEL에 남아 idleThreshold 뒤 다시 데드레터 기록을 시도한다
            log.error("데드레터 기록 실패, 나중에 다시 시도 - stream={}, recordId={}, 원인={}",
                streamKey, p.getId(), e.toString(), e);
            return;
        }
        redis.opsForStream().acknowledge(streamKey, group, p.getId());
        metrics.deadLettered(streamKey);
    }

    private static String prefix(String payload) {
        return payload.length() <= LOG_PAYLOAD_PREFIX ? payload : payload.substring(0, LOG_PAYLOAD_PREFIX) + "…";
    }

    private ObjectRecord<String, String> claim(RecordId id) {
        List<MapRecord<String, Object, Object>> claimed =
            redis.opsForStream().claim(streamKey, group, claimer, idleThreshold, id);
        if (claimed.isEmpty()) return null;
        MapRecord<String, Object, Object> raw = claimed.get(0);
        Iterator<Object> values = raw.getValue().values().iterator();
        String value = values.hasNext() ? String.valueOf(values.next()) : "";
        return StreamRecords.newRecord().in(streamKey).withId(raw.getId()).ofObject(value);
    }
}
