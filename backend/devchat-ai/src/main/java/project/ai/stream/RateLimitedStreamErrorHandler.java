package project.ai.stream;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.stream.Record;
import org.springframework.util.ErrorHandler;

/**
 * Redis Stream 구독 하나의 에러 처리기. 구독을 끊지 않고 로그만 남긴다(구독 유지는 cancelOnError=false가 맡는다).
 *
 * 1. 읽기 오류(DataAccessException): Redis가 내려가 있는 동안에는 폴링할 때마다 같은 오류가 나서, 그대로 찍으면 로그가 넘친다.
 *    그래서 스트림마다 LOG_INTERVAL에 한 번만 찍고, 그사이 생략한 건수를 다음 로그에 붙인다.
 *    또 잠깐 쉰다. 폴링 루프는 오류 뒤 바로 다시 읽기 때문에, Redis 이름을 못 찾거나 연결이 바로 거절되면 쉬지 않고 도는
 *    루프가 된다. 이 메서드는 폴링 스레드에서 불리므로 여기서 쉬면 그 스트림의 다음 읽기만 늦어진다.
 * 2. 그룹이 없다는 오류(NOGROUP): redis-stream이 데이터 없이 다시 뜨면 그룹이 사라진다. 이때 구독은 살아 있어도
 *    영영 읽지 못하므로 그룹을 다시 만든다. 대기 시간(READ_ERROR_BACKOFF)마다 한 번까지만 시도한다.
 * 3. 리스너 오류(메시지 변환 실패 등)는 메시지 하나의 문제라 쉬지 않는다. 읽기 오류의 로그 제한에 묻히지 않게
 *    매번 메시지 id와 함께 남긴다.
 */
@Slf4j
public class RateLimitedStreamErrorHandler implements ErrorHandler {

    static final Duration LOG_INTERVAL = Duration.ofSeconds(60);
    static final Duration READ_ERROR_BACKOFF = Duration.ofSeconds(1);
    private static final long NEVER = Long.MIN_VALUE;

    private final String streamKey;
    private final Runnable groupRecreator;
    private final LongSupplier nanoClock;
    private final Consumer<Duration> sleeper;
    private final AtomicLong lastLoggedAt = new AtomicLong(NEVER);
    private final AtomicLong lastGroupRecreatedAt = new AtomicLong(NEVER);
    private final AtomicLong suppressed = new AtomicLong();

    public RateLimitedStreamErrorHandler(String streamKey, Runnable groupRecreator) {
        this(streamKey, groupRecreator, System::nanoTime, RateLimitedStreamErrorHandler::sleep);
    }

    RateLimitedStreamErrorHandler(String streamKey, Runnable groupRecreator, LongSupplier nanoClock,
                                  Consumer<Duration> sleeper) {
        this.streamKey = streamKey;
        this.groupRecreator = groupRecreator;
        this.nanoClock = nanoClock;
        this.sleeper = sleeper;
    }

    @Override
    public void handleError(Throwable t) {
        if (!(t instanceof DataAccessException)) {
            log.error("Redis Stream 메시지 처리 실패, 구독은 유지함. stream={}, recordId={}", streamKey, recordId(t), t);
            return;
        }

        if (isNoGroup(t)) {
            recreateGroup();
        }

        if (tryAcquire(lastLoggedAt, LOG_INTERVAL)) {
            long skipped = suppressed.getAndSet(0);
            log.warn("Redis Stream 읽기 실패, 구독은 유지하고 다시 읽음. stream={}, 직전 {}초 동안 생략한 오류 {}건",
                streamKey, LOG_INTERVAL.toSeconds(), skipped, t);
        } else {
            suppressed.incrementAndGet();
        }

        sleeper.accept(READ_ERROR_BACKOFF);
    }

    long suppressedCount() {
        return suppressed.get();
    }

    private void recreateGroup() {
        if (!tryAcquire(lastGroupRecreatedAt, READ_ERROR_BACKOFF)) return;
        log.warn("컨슈머 그룹이 없어(NOGROUP) 다시 만듦. stream={}", streamKey);
        try {
            groupRecreator.run();
        } catch (RuntimeException e) {
            log.error("컨슈머 그룹 다시 만들기 실패. stream={}", streamKey, e);
        }
    }

    static boolean isNoGroup(Throwable t) {
        for (Throwable c = t; c != null; c = (c.getCause() == c) ? null : c.getCause()) {
            if (c.getMessage() != null && c.getMessage().contains("NOGROUP")) return true;
        }
        return false;
    }

    private static Object recordId(Throwable t) {
        if (t instanceof StreamListenerException e) return e.getRecordId();
        if (t instanceof ConversionFailedException e && e.getValue() instanceof Record<?, ?> record) return record.getId();
        return "알 수 없음";
    }

    private boolean tryAcquire(AtomicLong last, Duration interval) {
        long now = nanoClock.getAsLong();
        long prev = last.get();
        if (prev != NEVER && now - prev < interval.toNanos()) {
            return false;
        }
        return last.compareAndSet(prev, now);
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            // 컨테이너가 멈추는 중이다. 플래그를 되살려 두면 폴링 루프가 다음 바퀴에서 구독을 정리한다.
            Thread.currentThread().interrupt();
        }
    }
}
