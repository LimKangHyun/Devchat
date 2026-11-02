package project.ai.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.autoconfigure.health.HealthContributorAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.health.HealthEndpointAutoConfiguration;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * application.yml의 streams 헬스 그룹이 이 지표를 이름(redisStreamSubscriptions)으로 찾는지 확인한다.
 * 이름이 어긋나면 그룹 검증(validate-group-membership) 때문에 AI 서버가 뜨지 않는다.
 */
class RedisStreamSubscriptionsHealthGroupTest {

    @Test
    void streams_그룹에서_구독_상태를_볼_수_있다() {
        StreamSubscriptions subscriptions = mock(StreamSubscriptions.class);
        when(subscriptions.isContainerRunning()).thenReturn(true);
        when(subscriptions.activeByStream()).thenReturn(Map.of("stream:repo-index:request", false));

        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                HealthContributorAutoConfiguration.class, HealthEndpointAutoConfiguration.class))
            .withBean(StreamSubscriptions.class, () -> subscriptions)
            .withBean(RedisStreamSubscriptionsHealthIndicator.class)
            .withPropertyValues(
                "management.endpoint.health.group.streams.include=redisStreamSubscriptions",
                "management.endpoint.health.group.streams.show-details=always")
            .run(context -> {
                assertThat(context).hasNotFailed();
                HealthEndpoint endpoint = context.getBean(HealthEndpoint.class);
                assertThat(endpoint.healthForPath("streams").getStatus()).isEqualTo(Status.DOWN);
            });
    }
}
