package project.ai.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer.ConsumerStreamReadRequest;
import org.springframework.data.redis.stream.Subscription;

class StreamSubscriptionsTest {

    private static final String REPO_INDEX = "stream:repo-index:request";
    private static final String AI_REVIEW = "stream:ai-review:request";

    private StreamMessageListenerContainer<String, ObjectRecord<String, String>> container;
    private PrometheusMeterRegistry registry;
    private StreamSubscriptions subscriptions;
    private Subscription repoIndexSub;
    private Subscription aiReviewSub;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        container = mock(StreamMessageListenerContainer.class);
        registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        repoIndexSub = mock(Subscription.class);
        aiReviewSub = mock(Subscription.class);
        when(container.register(any(), any())).thenReturn(repoIndexSub, aiReviewSub);
        when(container.isRunning()).thenReturn(true);
        when(repoIndexSub.isActive()).thenReturn(true);
        when(aiReviewSub.isActive()).thenReturn(true);

        subscriptions = new StreamSubscriptions(container, registry);
        subscriptions.register(REPO_INDEX, "ai-repo-index-group", "ai-repo-index-1", record -> { });
        subscriptions.register(AI_REVIEW, "ai-review-group", "ai-review-1", record -> { });
    }

    @Test
    void 읽기_요청은_오류가_나도_구독을_끊지_않고_ACK는_직접_한다() {
        ConsumerStreamReadRequest<String> request = StreamSubscriptions.readRequest(
            REPO_INDEX, "ai-repo-index-group", "ai-repo-index-1", new RateLimitedStreamErrorHandler(REPO_INDEX));

        // container.receive()의 기본값(cancelOnError=true)이면 Redis 오류 한 번에 구독이 영구히 취소된다
        assertThat(request.getCancelSubscriptionOnError().test(new RedisConnectionFailureException("down"))).isFalse();
        assertThat(request.getCancelSubscriptionOnError().test(new IllegalStateException("listener"))).isFalse();
        assertThat(request.isAutoAcknowledge()).isFalse();
        assertThat(request.getConsumer().getGroup()).isEqualTo("ai-repo-index-group");
        assertThat(request.getConsumer().getName()).isEqualTo("ai-repo-index-1");
        assertThat(request.getStreamOffset().getKey()).isEqualTo(REPO_INDEX);
        assertThat(request.getStreamOffset().getOffset()).isEqualTo(ReadOffset.lastConsumed());
        assertThat(request.getErrorHandler()).isInstanceOf(RateLimitedStreamErrorHandler.class);
    }

    @Test
    void 모든_구독이_살아_있으면_헬스는_UP이고_지표는_1이다() {
        Health health = new RedisStreamSubscriptionsHealthIndicator(subscriptions).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry(REPO_INDEX, "active").containsEntry("containerRunning", true);
        assertThat(registry.scrape())
            .contains("devchat_stream_subscription_active{stream=\"" + REPO_INDEX + "\"} 1.0")
            .contains("devchat_stream_subscription_active{stream=\"" + AI_REVIEW + "\"} 1.0");
    }

    @Test
    void 구독_하나가_끊기면_헬스는_DOWN이고_그_스트림_지표만_0이다() {
        when(repoIndexSub.isActive()).thenReturn(false);

        Health health = new RedisStreamSubscriptionsHealthIndicator(subscriptions).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry(REPO_INDEX, "inactive").containsEntry(AI_REVIEW, "active");
        assertThat(registry.scrape())
            .contains("devchat_stream_subscription_active{stream=\"" + REPO_INDEX + "\"} 0.0")
            .contains("devchat_stream_subscription_active{stream=\"" + AI_REVIEW + "\"} 1.0");
    }

    @Test
    void 컨테이너가_멈추면_구독이_남아_있어도_DOWN이다() {
        when(container.isRunning()).thenReturn(false);

        Health health = new RedisStreamSubscriptionsHealthIndicator(subscriptions).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("containerRunning", false).containsEntry(AI_REVIEW, "inactive");
        assertThat(registry.scrape()).contains("devchat_stream_subscription_active{stream=\"" + AI_REVIEW + "\"} 0.0");
    }

    @Test
    void 등록된_구독이_없으면_DOWN이다() {
        StreamSubscriptions empty = new StreamSubscriptions(container, registry);

        assertThat(new RedisStreamSubscriptionsHealthIndicator(empty).health().getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    void 같은_오류가_반복되면_정해진_간격에_한_번만_로그를_남긴다() {
        AtomicLong now = new AtomicLong(0);
        List<Duration> sleeps = new ArrayList<>();
        var handler = new RateLimitedStreamErrorHandler(REPO_INDEX, now::get, sleeps::add);
        RedisConnectionFailureException down = new RedisConnectionFailureException("redis-stream down");

        handler.handleError(down); // 첫 오류는 바로 남긴다
        now.addAndGet(Duration.ofSeconds(2).toNanos());
        handler.handleError(down);
        now.addAndGet(Duration.ofSeconds(2).toNanos());
        handler.handleError(down);
        assertThat(handler.suppressedCount()).isEqualTo(2);

        now.addAndGet(RateLimitedStreamErrorHandler.LOG_INTERVAL.toNanos());
        handler.handleError(down); // 간격이 지나면 다시 남기고 생략 건수를 비운다
        assertThat(handler.suppressedCount()).isZero();

        // 읽기 오류마다 잠깐 쉬어서, Redis가 없는 동안 폴링 루프가 쉬지 않고 돌지 않게 한다
        assertThat(sleeps).hasSize(4).containsOnly(RateLimitedStreamErrorHandler.READ_ERROR_BACKOFF);
    }

    @Test
    void 리스너_오류는_쉬지_않는다() {
        List<Duration> sleeps = new ArrayList<>();
        var handler = new RateLimitedStreamErrorHandler(REPO_INDEX, System::nanoTime, sleeps::add);

        handler.handleError(new IllegalArgumentException("bad payload"));

        assertThat(sleeps).isEmpty();
    }
}
