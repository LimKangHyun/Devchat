package project.ai.stream;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Redis Stream 구독 중 하나라도 끊겼거나 리스너 컨테이너가 멈췄으면 DOWN.
 * 이름은 redisStreamSubscriptions이고, /actuator/health/streams 그룹에서 스트림별 상태를 볼 수 있다(application.yml).
 *
 * Redis가 잠깐 내려가도 구독은 유지되므로(cancelOnError=false) 이 지표는 UP 그대로다.
 * Redis 연결 자체는 Spring Boot 기본 redis 헬스 체크가 따로 본다.
 */
@Component
@RequiredArgsConstructor
public class RedisStreamSubscriptionsHealthIndicator implements HealthIndicator {

    private final StreamSubscriptions subscriptions;

    @Override
    public Health health() {
        boolean running = subscriptions.isContainerRunning();
        Map<String, Boolean> byStream = subscriptions.activeByStream();
        boolean allActive = !byStream.isEmpty() && byStream.values().stream().allMatch(Boolean::booleanValue);

        Health.Builder builder = (running && allActive) ? Health.up() : Health.down();
        builder.withDetail("containerRunning", running);
        byStream.forEach((stream, active) -> builder.withDetail(stream, active ? "active" : "inactive"));
        return builder.build();
    }
}
