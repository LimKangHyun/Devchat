package project.ai.stream;

import java.time.Duration;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

/**
 * 스트림 폴링 대기 시간(pollTimeout)을 Redis 명령 시간 제한(spring.data.redis.timeout)보다 확실히 짧게 맞춘다.
 *
 * 리스너 컨테이너는 pollTimeout만큼 XREADGROUP ... BLOCK으로 기다린다. 그런데 Lettuce는 명령 시간 제한이 지나면
 * 응답을 기다리지 않고 RedisCommandTimeoutException을 던진다. 그래서 pollTimeout(2초) >= 명령 시간 제한(운영 500ms)이면
 * 1. 새 메시지가 없는 평범한 폴링마다 "Redis command timed out"이 나고,
 * 2. Redis가 아직 BLOCK 중일 때 메시지가 들어오면, Redis는 그 메시지를 이 컨슈머에게 넘기고(PEL에 올림) 응답을 보내지만
 *    클라이언트는 이미 포기해서 버린다. 그 메시지는 처리되지도 ACK되지도 않고 PEL에 영영 남는다(2026-07, 2026-11 장애).
 *
 * 설정을 잘못 바꿔도 소비가 조용히 깨지지 않게, 시작할 때 비교해서 너무 길면 줄이고 ERROR로 남긴다.
 * 기동을 막지는 않는다. deploy.sh가 AI 서버 상태를 확인하지 않아서, 재시작이 반복되면 더 알아채기 어렵다.
 */
@Slf4j
public final class StreamPollTimeout {

    /** 폴링 대기와 명령 시간 제한 사이에 둘 여유. 네트워크 왕복과 Redis 처리 시간을 덮는다. */
    static final Duration MARGIN = Duration.ofMillis(200);
    /** 여유를 빼고 남는 시간이 이보다 짧으면, 명령 시간 제한의 절반을 쓴다. */
    static final Duration MIN_POLL_TIMEOUT = Duration.ofMillis(100);

    private StreamPollTimeout() {
    }

    /**
     * desired가 명령 시간 제한 안에 여유 있게 들어가면 그대로, 아니면 줄인 값을 돌려준다.
     * commandTimeout을 알 수 없으면 desired를 그대로 쓴다.
     */
    public static Duration safe(Duration desired, Optional<Duration> commandTimeout) {
        if (commandTimeout.isEmpty()) return desired;
        Duration limit = commandTimeout.get();
        Duration safe = maxSafe(limit);
        if (desired.compareTo(safe) <= 0) return desired;

        log.error("Redis Stream 폴링 대기({}ms)가 Redis 명령 시간 제한({}ms)에 비해 너무 길어 {}ms로 줄임. "
                + "이대로면 새 메시지가 없을 때마다 'Redis command timed out'이 나고, 기다리는 사이 들어온 메시지는 "
                + "PEL에 남은 채 처리되지 않는다. spring.data.redis.timeout을 폴링 대기 + {}ms보다 길게 잡을 것.",
            desired.toMillis(), limit.toMillis(), safe.toMillis(), MARGIN.toMillis());
        return safe;
    }

    /** 명령 시간 제한 안에서 안전하게 기다릴 수 있는 가장 긴 폴링 대기. */
    static Duration maxSafe(Duration commandTimeout) {
        Duration withMargin = commandTimeout.minus(MARGIN);
        if (withMargin.compareTo(MIN_POLL_TIMEOUT) >= 0) return withMargin;
        // 명령 시간 제한 자체가 아주 짧다. 0(=무한 대기)이 되지 않게 절반을 쓰고, 그래도 최소 1ms
        // (BLOCK은 ms 단위라 ms 미만은 0으로 잘린다)
        return Duration.ofMillis(Math.max(1, commandTimeout.toMillis() / 2));
    }

    /** 연결 팩토리에 실제로 걸린 명령 시간 제한. Lettuce가 아니면 알 수 없다. */
    public static Optional<Duration> commandTimeoutOf(RedisConnectionFactory connectionFactory) {
        if (connectionFactory instanceof LettuceConnectionFactory lettuce) {
            return Optional.ofNullable(lettuce.getClientConfiguration().getCommandTimeout());
        }
        return Optional.empty();
    }
}
