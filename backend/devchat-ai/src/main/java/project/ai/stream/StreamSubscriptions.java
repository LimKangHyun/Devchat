package project.ai.stream;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiFunction;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer.ConsumerStreamReadRequest;
import org.springframework.data.redis.stream.StreamMessageListenerContainer.StreamReadRequest;
import org.springframework.data.redis.stream.Subscription;
import org.springframework.util.ErrorHandler;

/**
 * AI 서버가 읽는 Redis Stream 구독을 등록하고, 구독이 살아 있는지 보여 준다.
 *
 * 예전에는 container.receive()로 구독했는데, 이 방식은 cancelOnError가 켜져 있어 읽기 오류 한 번에 구독이 영구히 취소됐다.
 * 백엔드 배포 때마다 redis-stream을 다시 띄우는 순간 오류가 나면, AI 서버는 살아 있는데 그 스트림을 아무도 읽지 않았다
 * (2026-07 이후 인덱싱 요청이 전달되지 않은 장애). 그래서
 * 1. cancelOnError=false로 등록해 오류가 나도 구독을 유지하고,
 * 2. 등록한 Subscription을 들고 있다가 헬스 체크(RedisStreamSubscriptionsHealthIndicator)와
 *    지표(devchat_stream_subscription_active)로 끊긴 구독을 드러낸다.
 *
 * ACK는 컨테이너가 하지 않는다(autoAcknowledge=false). AI 리뷰는 Consumer가 직접, 나머지는 AckOnSuccessListener가
 * 처리에 성공했을 때 한다. 실패해 PEL에 남은 메시지는 PendingMessageReclaimer가 다시 처리한다.
 */
public class StreamSubscriptions {

    static final String ACTIVE_GAUGE = "devchat.stream.subscription.active";

    private final StreamMessageListenerContainer<String, ObjectRecord<String, String>> container;
    private final MeterRegistry meterRegistry;
    /** (stream, group) → 에러 처리기 */
    private final BiFunction<String, String, ErrorHandler> errorHandlerFactory;
    // 등록은 시작할 때 한 스레드에서만 하고, 그 뒤로는 읽기만 한다
    private final Map<String, Subscription> subscriptions = Collections.synchronizedMap(new LinkedHashMap<>());

    public StreamSubscriptions(StreamMessageListenerContainer<String, ObjectRecord<String, String>> container,
                               MeterRegistry meterRegistry, StreamConsumerGroups consumerGroups) {
        this(container, meterRegistry, (stream, group) ->
            new RateLimitedStreamErrorHandler(stream, () -> consumerGroups.createIfNotExists(stream, group)));
    }

    StreamSubscriptions(StreamMessageListenerContainer<String, ObjectRecord<String, String>> container,
                        MeterRegistry meterRegistry, BiFunction<String, String, ErrorHandler> errorHandlerFactory) {
        this.container = container;
        this.meterRegistry = meterRegistry;
        this.errorHandlerFactory = errorHandlerFactory;
    }

    /**
     * 컨테이너 설정. 한 번에 한 메시지만 읽는다(batchSize=1).
     * 여러 개를 한꺼번에 읽으면 기다리는 메시지까지 모두 PEL에 올라가 "마지막 전달 뒤 지난 시간"이 처리 시작이 아니라
     * 읽은 시각부터 흐른다. 앞 메시지들이 오래 걸리면(큰 push의 파일 재인덱싱 등) 아직 시작도 안 한 메시지가
     * 재처리 기준 시간을 넘겨, 재처리 스케줄러가 같은 메시지를 동시에 한 번 더 처리하거나 데드레터로 잘못 보낸다.
     */
    public static StreamMessageListenerContainer.StreamMessageListenerContainerOptions<String, ObjectRecord<String, String>>
            containerOptions(Duration pollTimeout) {
        return StreamMessageListenerContainer.StreamMessageListenerContainerOptions.builder()
            .pollTimeout(pollTimeout)
            .batchSize(1)
            .targetType(String.class)
            .build();
    }

    public void register(String streamKey, String group, String consumerName,
                         StreamListener<String, ObjectRecord<String, String>> listener) {
        Subscription subscription = container.register(
            readRequest(streamKey, group, consumerName, errorHandlerFactory.apply(streamKey, group)),
            withRecordId(listener));
        subscriptions.put(streamKey, subscription);

        Gauge.builder(ACTIVE_GAUGE, this, self -> self.isActive(streamKey) ? 1 : 0)
            .description("Redis Stream 구독이 살아 있으면 1, 끊겼으면 0")
            .tag("stream", streamKey)
            .register(meterRegistry);
    }

    /** 리스너 예외에 메시지 id를 붙여, 에러 로그에서 어떤 메시지였는지 보이게 한다. */
    private static StreamListener<String, ObjectRecord<String, String>> withRecordId(
            StreamListener<String, ObjectRecord<String, String>> listener) {
        return record -> {
            try {
                listener.onMessage(record);
            } catch (RuntimeException e) {
                throw new StreamListenerException(record.getId(), e);
            }
        };
    }

    static ConsumerStreamReadRequest<String> readRequest(String streamKey, String group, String consumerName,
                                                         ErrorHandler errorHandler) {
        return StreamReadRequest.builder(StreamOffset.create(streamKey, ReadOffset.lastConsumed()))
            .consumer(Consumer.from(group, consumerName))
            .autoAcknowledge(false)
            // 어떤 오류가 나도 구독을 끊지 않는다. Redis가 돌아오면 같은 구독이 이어서 읽는다
            .cancelOnError(t -> false)
            .errorHandler(errorHandler)
            .build();
    }

    public boolean isContainerRunning() {
        return container.isRunning();
    }

    public boolean isActive(String streamKey) {
        Subscription subscription = subscriptions.get(streamKey);
        return subscription != null && container.isRunning() && subscription.isActive();
    }

    /** 스트림별 구독 상태. 등록한 순서대로 돌려준다. */
    public Map<String, Boolean> activeByStream() {
        Map<String, Boolean> result = new LinkedHashMap<>();
        synchronized (subscriptions) {
            subscriptions.keySet().forEach(stream -> result.put(stream, isActive(stream)));
        }
        return result;
    }
}
