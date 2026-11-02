package project.ai.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import project.ai.client.QdrantClient.QdrantApiException;

/** Qdrant를 WireMock으로 흉내 내, 컬렉션 스키마 헬스·지표와 거절 이유 로그를 확인한다. */
class QdrantSchemaTest {

    private static final String COLLECTION = "devchat_hybrid";
    private static final String COLLECTION_URL = "/collections/" + COLLECTION;

    private WireMockServer qdrant;
    private ListAppender<ILoggingEvent> logs;
    private Logger clientLogger;

    @BeforeEach
    void setUp() {
        qdrant = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        qdrant.start();
        // 시작할 때 컬렉션이 이미 있고 인덱스 생성이 되는 상태
        qdrant.stubFor(get(urlEqualTo(COLLECTION_URL + "/exists"))
                .willReturn(json(200, "{\"result\":{\"exists\":true}}")));
        qdrant.stubFor(put(urlPathMatching(COLLECTION_URL + "/index.*")).willReturn(json(200, "{}")));

        clientLogger = (Logger) LoggerFactory.getLogger(QdrantClient.class);
        logs = new ListAppender<>();
        logs.start();
        clientLogger.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        clientLogger.detachAppender(logs);
        qdrant.stop();
    }

    @Test
    void 예전_스키마면_헬스는_DOWN이고_지표는_0() {
        stubCollectionInfo("{\"size\":3072,\"distance\":\"Cosine\"}");
        QdrantClient client = newClient();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        QdrantHealthIndicator indicator = new QdrantHealthIndicator(client, registry, Duration.ofMinutes(5));

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("collection", COLLECTION).containsEntry("schema", "legacy");
        assertThat(registry.get(QdrantHealthIndicator.SCHEMA_GAUGE).gauge().value()).isEqualTo(0.0);
    }

    @Test
    void 하이브리드_스키마면_헬스는_UP이고_지표는_1() {
        stubCollectionInfo("{\"dense\":{\"size\":3072,\"distance\":\"Cosine\"}}");
        QdrantClient client = newClient();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        QdrantHealthIndicator indicator = new QdrantHealthIndicator(client, registry, Duration.ofMinutes(5));

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("schema", "hybrid");
        assertThat(registry.get(QdrantHealthIndicator.SCHEMA_GAUGE).gauge().value()).isEqualTo(1.0);
    }

    @Test
    void 스키마_확인은_캐시되어_헬스를_여러_번_불러도_Qdrant는_한_번만_부른다() {
        stubCollectionInfo("{\"dense\":{\"size\":3072,\"distance\":\"Cosine\"}}");
        QdrantClient client = newClient();
        int callsAtStartup = qdrant.findAll(getRequestedFor(urlEqualTo(COLLECTION_URL))).size();
        QdrantHealthIndicator indicator =
                new QdrantHealthIndicator(client, new SimpleMeterRegistry(), Duration.ofMinutes(5));

        indicator.health();
        indicator.health();
        indicator.health();

        assertThat(qdrant.findAll(getRequestedFor(urlEqualTo(COLLECTION_URL))).size() - callsAtStartup).isEqualTo(1);
    }

    @Test
    void 컬렉션이_없으면_UNKNOWN_missing() {
        qdrant.stubFor(get(urlEqualTo(COLLECTION_URL)).willReturn(json(404, "{\"status\":{\"error\":\"Not found\"}}")));
        QdrantClient client = newClient();

        Health health = new QdrantHealthIndicator(client, new SimpleMeterRegistry(), Duration.ofMinutes(5)).health();

        assertThat(health.getStatus()).isEqualTo(Status.UNKNOWN);
        assertThat(health.getDetails()).containsEntry("schema", "missing");
    }

    @Test
    void 연결할_수_없으면_UNKNOWN이고_지표는_0이_아니다() {
        QdrantClient client = newClient();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        qdrant.stop();

        Health health = new QdrantHealthIndicator(client, registry, Duration.ofMinutes(5)).health();

        assertThat(health.getStatus()).isEqualTo(Status.UNKNOWN);
        assertThat(health.getDetails()).containsEntry("schema", "unknown");
        assertThat(registry.get(QdrantHealthIndicator.SCHEMA_GAUGE).gauge().value()).isEqualTo(1.0);
    }

    @Test
    void 저장이_400으로_거절되면_거절_이유와_해결_방법을_ERROR로_남긴다() {
        stubCollectionInfo("{\"size\":3072,\"distance\":\"Cosine\"}");
        qdrant.stubFor(put(urlPathMatching(COLLECTION_URL + "/points.*")).willReturn(json(400,
                "{\"status\":{\"error\":\"Wrong input: Not existing vector name error: dense\"}}")));
        QdrantClient client = newClient();
        QdrantClient.Point point = new QdrantClient.Point(
                "00000000-0000-0000-0000-000000000001", new float[] {0.1f, 0.2f}, null, Map.of("code", "SECRET_PAYLOAD"));

        assertThatThrownBy(() -> client.upsert(List.of(point)))
                .isInstanceOf(QdrantApiException.class)
                .satisfies(e -> assertThat(((QdrantApiException) e).getStatus()).isEqualTo(400));

        List<String> errors = errorLogs();
        assertThat(errors).anySatisfy(m -> assertThat(m)
                .contains("operation=upsert", "collection=" + COLLECTION, "status=400", "Not existing vector name"));
        assertThat(errors).anySatisfy(m -> assertThat(m).contains("예전 스키마", "QDRANT_COLLECTION"));
        assertThat(errors).noneSatisfy(m -> assertThat(m).contains("SECRET_PAYLOAD"));
    }

    @Test
    void 긴_거절_본문은_1KB로_자른다() {
        qdrant.stubFor(put(urlPathMatching(COLLECTION_URL + "/points.*")).willReturn(json(500, "x".repeat(5000))));
        QdrantClient client = newClient();
        QdrantClient.Point point = new QdrantClient.Point(
                "00000000-0000-0000-0000-000000000001", new float[] {0.1f}, null, Map.of());

        assertThatThrownBy(() -> client.upsert(List.of(point))).isInstanceOf(QdrantApiException.class);

        String message = errorLogs().stream().filter(m -> m.contains("status=500")).findFirst().orElseThrow();
        assertThat(message.length()).isLessThan(1300);
    }

    private List<String> errorLogs() {
        return logs.list.stream()
                .filter(e -> e.getLevel() == Level.ERROR)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    private void stubCollectionInfo(String vectorsJson) {
        qdrant.stubFor(get(urlEqualTo(COLLECTION_URL)).willReturn(json(200,
                "{\"result\":{\"config\":{\"params\":{\"vectors\":" + vectorsJson + "}}}}")));
    }

    private static ResponseDefinitionBuilder json(int status, String body) {
        return aResponse().withStatus(status).withHeader("Content-Type", "application/json").withBody(body);
    }

    private QdrantClient newClient() {
        QdrantClient client = new QdrantClient(WebClient.builder());
        ReflectionTestUtils.setField(client, "url", "http://localhost:" + qdrant.port());
        ReflectionTestUtils.setField(client, "apiKey", "test-key");
        ReflectionTestUtils.setField(client, "collection", COLLECTION);
        ReflectionTestUtils.setField(client, "maxConcurrentCalls", 5);
        ReflectionTestUtils.setField(client, "searchTimeout", Duration.ofSeconds(5));
        ReflectionTestUtils.setField(client, "writeTimeout", Duration.ofSeconds(5));
        client.init();
        return client;
    }
}
