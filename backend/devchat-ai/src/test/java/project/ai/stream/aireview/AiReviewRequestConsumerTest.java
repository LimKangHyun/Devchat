package project.ai.stream.aireview;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import project.ai.client.internal.AiReviewInternalApiClient;
import project.ai.processor.AiReviewProcessor;
import project.common.dto.InlineReview;
import project.common.message.aireview.AiReviewRequestMessage;
import project.common.message.aireview.FileClaimResult;

class AiReviewRequestConsumerTest {

    private static final String RECORD_ID = "1700000000000-0";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private AiReviewProcessor processor;
    private AiReviewResultProducer producer;
    private AiReviewInternalApiClient apiClient;
    private AiReviewHeartbeat heartbeat;
    private StreamOperations<String, Object, Object> streamOps;
    private AiReviewRequestConsumer consumer;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        processor = mock(AiReviewProcessor.class);
        producer = mock(AiReviewResultProducer.class);
        apiClient = mock(AiReviewInternalApiClient.class);
        heartbeat = mock(AiReviewHeartbeat.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        streamOps = mock(StreamOperations.class);
        when(redis.opsForStream()).thenReturn(streamOps);
        consumer = new AiReviewRequestConsumer(objectMapper, processor, producer, apiClient, heartbeat, redis);
    }

    private ObjectRecord<String, String> record() throws Exception {
        AiReviewRequestMessage message = new AiReviewRequestMessage(1L, 2L, 2L, "A.java", "diff", "content", "",
            "title", "body", List.of("A.java"), null, null, "sha-1");
        return StreamRecords.newRecord().in(AiReviewRequestConsumer.STREAM_KEY)
            .withId(RecordId.of(RECORD_ID)).ofObject(objectMapper.writeValueAsString(message));
    }

    private void verifyAcked(boolean acked) {
        if (acked) verify(streamOps).acknowledge(AiReviewRequestConsumer.STREAM_KEY, AiReviewRequestConsumer.GROUP_NAME, RECORD_ID);
        else verify(streamOps, never()).acknowledge(anyString(), anyString(), any(String[].class));
    }

    @Test
    void API_서버에_닿지_못하면_ACK하지_않아_나중에_재시도한다() throws Exception {
        given(apiClient.claim(1L, "A.java", "sha-1", false)).willThrow(new RuntimeException("connection refused"));

        consumer.onMessage(record());

        verify(processor, never()).process(any());
        verifyAcked(false);
    }

    @Test
    void 다른_Worker가_처리_중이거나_예전_커밋이면_처리하지_않고_ACK한다() throws Exception {
        given(apiClient.claim(1L, "A.java", "sha-1", false)).willReturn(FileClaimResult.ALREADY_TAKEN);

        consumer.onMessage(record());

        verify(processor, never()).process(any());
        verify(heartbeat, never()).start(anyString());
        verifyAcked(true);
    }

    @Test
    void 처리_권한을_얻으면_Heartbeat를_켜고_리뷰한_뒤_커밋과_함께_결과를_보내고_ACK한다() throws Exception {
        given(apiClient.claim(1L, "A.java", "sha-1", false)).willReturn(FileClaimResult.CLAIMED);
        List<InlineReview> reviews = List.of();
        given(processor.process(any())).willReturn(reviews);

        consumer.onMessage(record());

        verify(heartbeat).start(RECORD_ID);
        verify(producer).publishSuccess(1L, 2L, "A.java", "sha-1", reviews);
        verifyAcked(true);
        verify(heartbeat).stop(RECORD_ID);
    }

    @Test
    void 리뷰가_실패하면_실패_결과를_보내고_ACK한다() throws Exception {
        given(apiClient.claim(1L, "A.java", "sha-1", false)).willReturn(FileClaimResult.CLAIMED);
        given(processor.process(any())).willThrow(new RuntimeException("gemini down"));

        consumer.onMessage(record());

        verify(producer).publishFail(1L, 2L, "A.java", "sha-1", "gemini down");
        verifyAcked(true);
    }

    @Test
    void 결과_발행이_실패하면_ACK하지_않고_Heartbeat를_지워_곧_재처리되게_한다() throws Exception {
        given(apiClient.claim(1L, "A.java", "sha-1", false)).willReturn(FileClaimResult.CLAIMED);
        given(processor.process(any())).willReturn(List.of());
        willThrow(new AiReviewResultProducer.ResultPublishException("redis down", null))
            .given(producer).publishSuccess(eq(1L), eq(2L), eq("A.java"), eq("sha-1"), any());

        consumer.onMessage(record());

        verifyAcked(false);
        verify(heartbeat).stop(RECORD_ID);
    }

    @Test
    void 역직렬화에_실패한_메시지는_버린다() {
        consumer.onMessage(StreamRecords.newRecord().in(AiReviewRequestConsumer.STREAM_KEY)
            .withId(RecordId.of(RECORD_ID)).ofObject("not-json"));

        verify(apiClient, never()).claim(any(), anyString(), anyString(), anyBoolean());
        verifyAcked(true);
    }
}
