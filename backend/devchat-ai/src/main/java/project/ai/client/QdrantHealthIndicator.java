package project.ai.client;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.stereotype.Component;
import project.ai.client.QdrantClient.SchemaStatus;

/**
 * Qdrant 컬렉션 스키마를 /actuator/health(qdrant)와 지표 devchat_qdrant_schema_ok로 드러낸다.
 *
 * 예전 스키마(이름 없는 벡터) 컬렉션은 서버가 떠 있고 로그도 시작할 때 한 줄뿐이라, 인덱싱 요청이 올 때까지 아무도 모른다(2026-11 장애).
 * - hybrid: UP, legacy: DOWN, 컬렉션 없음·연결 실패: UNKNOWN (없는 컬렉션은 시작할 때 만들어지므로 일시적)
 * - 헬스 호출과 지표 수집마다 Qdrant를 부르지 않도록 결과를 캐시하고 refresh-interval마다만 다시 확인한다.
 *   연결 실패(UNKNOWN)는 빨리 회복을 보려고 1분 뒤 다시 확인한다.
 * - 지표 값은 legacy일 때만 0이다. 연결 실패는 스키마 문제가 아니므로 1로 둔다(Qdrant 장애는 다른 지표가 본다).
 */
@Component
public class QdrantHealthIndicator implements HealthIndicator {

    static final String SCHEMA_GAUGE = "devchat_qdrant_schema_ok";
    private static final Duration UNKNOWN_RETRY = Duration.ofMinutes(1);

    private final QdrantClient qdrantClient;
    private final Duration refreshInterval;
    private final ReentrantLock lock = new ReentrantLock();

    private volatile SchemaStatus cached;
    private volatile long checkedAtNanos;

    public QdrantHealthIndicator(QdrantClient qdrantClient, MeterRegistry meterRegistry,
                                 @Value("${qdrant.schema-check-interval:5m}") Duration refreshInterval) {
        this.qdrantClient = qdrantClient;
        this.refreshInterval = refreshInterval;
        Gauge.builder(SCHEMA_GAUGE, this, self -> self.schema() == SchemaStatus.LEGACY ? 0 : 1)
                .description("Qdrant 컬렉션이 하이브리드 스키마가 아니면 0 (예전 스키마)")
                .register(meterRegistry);
    }

    @Override
    public Health health() {
        SchemaStatus schema = schema();
        Status status = switch (schema) {
            case HYBRID -> Status.UP;
            case LEGACY -> Status.DOWN;
            case MISSING, UNKNOWN -> Status.UNKNOWN;
        };
        return Health.status(status)
                .withDetail("collection", qdrantClient.collectionName())
                .withDetail("schema", schema.name().toLowerCase())
                .build();
    }

    private SchemaStatus schema() {
        SchemaStatus current = cached;
        if (current != null && !isStale(current)) return current;

        // 동시에 여럿이 와도 Qdrant는 한 번만 부른다. 기다리지 않고 직전 값을 돌려준다.
        if (!lock.tryLock()) return current != null ? current : SchemaStatus.UNKNOWN;
        try {
            if (cached == null || isStale(cached)) {
                cached = qdrantClient.inspectSchema();
                checkedAtNanos = System.nanoTime();
            }
            return cached;
        } finally {
            lock.unlock();
        }
    }

    private boolean isStale(SchemaStatus status) {
        Duration ttl = status == SchemaStatus.UNKNOWN || status == SchemaStatus.MISSING ? UNKNOWN_RETRY : refreshInterval;
        return System.nanoTime() - checkedAtNanos > ttl.toNanos();
    }
}
