package project.ai.stream.aireview;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import project.ai.client.internal.AiReviewInternalApiClient;
import project.common.message.aireview.AiReviewRequestMessage;

class AiReviewPelReclaimSchedulerTest {

    private static final String ID = "1700000000000-0";
    private static final String STREAM = AiReviewRequestConsumer.STREAM_KEY;
    private static final String GROUP = AiReviewRequestConsumer.GROUP_NAME;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private StreamOperations<String, Object, Object> streamOps;
    private AiReviewRequestConsumer consumer;
    private AiReviewHeartbeat heartbeat;
    private AiReviewResultProducer producer;
    private AiReviewInternalApiClient apiClient;
    private AiReviewPelReclaimScheduler scheduler;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        streamOps = mock(StreamOperations.class);
        when(redis.opsForStream()).thenReturn(streamOps);
        consumer = mock(AiReviewRequestConsumer.class);
        heartbeat = mock(AiReviewHeartbeat.class);
        producer = mock(AiReviewResultProducer.class);
        apiClient = mock(AiReviewInternalApiClient.class);
        scheduler = new AiReviewPelReclaimScheduler(redis, consumer, heartbeat, producer, apiClient, objectMapper);
    }

    private void pending(Duration idle, long deliveryCount) {
        PendingMessage message = new PendingMessage(RecordId.of(ID), Consumer.from(GROUP, "ai-review-1"), idle, deliveryCount);
        when(streamOps.pending(eq(STREAM), eq(GROUP), any(Range.class), any(Long.class)))
            .thenReturn(new PendingMessages(GROUP, List.of(message)));
    }

    private String payload() throws Exception {
        return objectMapper.writeValueAsString(new AiReviewRequestMessage(1L, 2L, 2L, "A.java", "d", "c", "",
            "t", "b", List.of(), null, null, "sha-1"));
    }

    @Test
    void 아직_5분이_안_지났으면_건드리지_않는다() {
        pending(Duration.ofMinutes(2), 1);

        scheduler.reclaimStaleMessages();

        verify(streamOps, never()).claim(anyString(), anyString(), anyString(), any(Duration.class), any(RecordId[].class));
    }

    @Test
    void 오래_걸려도_Heartbeat가_살아_있으면_처리_중으로_보고_건드리지_않는다() {
        pending(Duration.ofMinutes(8), 1);
        when(heartbeat.isAlive(ID)).thenReturn(true);

        scheduler.reclaimStaleMessages();

        verify(streamOps, never()).claim(anyString(), anyString(), anyString(), any(Duration.class), any(RecordId[].class));
        verify(consumer, never()).handle(any(), anyBoolean());
    }

    @Test
    void Heartbeat가_만료됐으면_XCLAIM으로_가져와_처리_권한을_넘겨받아_다시_처리한다() throws Exception {
        pending(Duration.ofMinutes(8), 1);
        when(heartbeat.isAlive(ID)).thenReturn(false);
        MapRecord<String, Object, Object> raw = StreamRecords.newRecord().in(STREAM)
            .withId(RecordId.of(ID)).ofMap(Map.<Object, Object>of("payload", payload()));
        when(streamOps.claim(eq(STREAM), eq(GROUP), anyString(), any(Duration.class), any(RecordId[].class)))
            .thenReturn(List.of(raw));

        scheduler.reclaimStaleMessages();

        verify(consumer).handle(any(), eq(true));
    }

    @Test
    void 전달_횟수가_3번을_넘으면_데드레터로_옮기고_실패_결과를_보낸_뒤_ACK한다() throws Exception {
        pending(Duration.ofMinutes(8), 4);
        when(heartbeat.isAlive(ID)).thenReturn(false);
        String payload = payload();
        MapRecord<String, Object, Object> raw = StreamRecords.newRecord().in(STREAM)
            .withId(RecordId.of(ID)).ofMap(Map.<Object, Object>of("payload", payload));
        when(streamOps.range(eq(STREAM), any(Range.class))).thenReturn(List.of(raw));

        scheduler.reclaimStaleMessages();

        verify(apiClient).reportDeadLetter(STREAM, ID, payload);
        verify(producer).publishFail(eq(1L), eq(2L), eq("A.java"), eq("sha-1"), anyString());
        verify(streamOps).acknowledge(STREAM, GROUP, RecordId.of(ID));
        verify(consumer, never()).handle(any(), anyBoolean());
    }
}
