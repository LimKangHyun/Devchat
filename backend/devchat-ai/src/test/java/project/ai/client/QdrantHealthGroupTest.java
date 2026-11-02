package project.ai.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.autoconfigure.health.HealthContributorAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.health.HealthEndpointAutoConfiguration;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** application.yml의 qdrant 헬스 그룹이 이 지표를 이름(qdrant)으로 찾는지 확인한다. 어긋나면 AI 서버가 뜨지 않는다. */
class QdrantHealthGroupTest {

    @Test
    void qdrant_그룹에서_스키마_상태를_볼_수_있다() {
        QdrantClient client = mock(QdrantClient.class);
        when(client.inspectSchema()).thenReturn(QdrantClient.SchemaStatus.LEGACY);
        when(client.collectionName()).thenReturn("devchat");

        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                HealthContributorAutoConfiguration.class, HealthEndpointAutoConfiguration.class))
            .withInitializer(ctx -> ctx.getBeanFactory().setConversionService(
                org.springframework.boot.convert.ApplicationConversionService.getSharedInstance()))
            .withBean(QdrantClient.class, () -> client)
            .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
            .withBean(QdrantHealthIndicator.class, () ->
                new QdrantHealthIndicator(client, new SimpleMeterRegistry(), Duration.ofMinutes(5)))
            .withPropertyValues(
                "management.endpoint.health.group.qdrant.include=qdrant",
                "management.endpoint.health.group.qdrant.show-details=always")
            .run(context -> {
                assertThat(context).hasNotFailed();
                HealthEndpoint endpoint = context.getBean(HealthEndpoint.class);
                assertThat(endpoint.healthForPath("qdrant").getStatus()).isEqualTo(Status.DOWN);
            });
    }
}
