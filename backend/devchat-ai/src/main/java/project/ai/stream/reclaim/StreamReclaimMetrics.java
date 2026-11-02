package project.ai.stream.reclaim;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 스트림 재처리 지표.
 * - devchat_stream_reclaimed_total{stream}: 멈춘 메시지를 가져와 다시 처리한 횟수
 * - devchat_stream_dead_letter_total{stream}: 최대 전달 횟수를 넘겨 데드레터로 보낸 수 (0보다 크면 알림)
 * - devchat_stream_pending{stream}: ACK 안 된 메시지 수. 재처리 주기마다(30초) XPENDING 요약으로 갱신한다
 */
public class StreamReclaimMetrics {

    static final String RECLAIMED = "devchat.stream.reclaimed";
    static final String DEAD_LETTER = "devchat.stream.dead.letter";
    static final String PENDING = "devchat.stream.pending";

    private final MeterRegistry registry;
    private final Map<String, AtomicLong> pending = new ConcurrentHashMap<>();

    public StreamReclaimMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /** 처음부터 0으로 보이게 미리 만든다. 그래야 increase()가 첫 데드레터도 잡는다. */
    public void register(String streamKey) {
        counter(RECLAIMED, "멈춘 스트림 메시지를 가져와 다시 처리한 횟수", streamKey);
        counter(DEAD_LETTER, "최대 전달 횟수를 넘겨 데드레터로 보낸 스트림 메시지 수", streamKey);
    }

    public void reclaimed(String streamKey) {
        counter(RECLAIMED, "멈춘 스트림 메시지를 가져와 다시 처리한 횟수", streamKey).increment();
    }

    public void deadLettered(String streamKey) {
        counter(DEAD_LETTER, "최대 전달 횟수를 넘겨 데드레터로 보낸 스트림 메시지 수", streamKey).increment();
    }

    public void pending(String streamKey, long count) {
        pending.computeIfAbsent(streamKey, key -> {
            AtomicLong value = new AtomicLong();
            Gauge.builder(PENDING, value, AtomicLong::get)
                .description("ACK 안 된 스트림 메시지 수 (XPENDING 요약)")
                .tag("stream", key)
                .register(registry);
            return value;
        }).set(count);
    }

    private Counter counter(String name, String description, String streamKey) {
        return Counter.builder(name).description(description).tag("stream", streamKey).register(registry);
    }
}
