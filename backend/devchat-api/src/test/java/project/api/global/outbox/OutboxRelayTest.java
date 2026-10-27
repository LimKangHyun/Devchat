package project.api.global.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class OutboxRelayTest {

    OutboxService outboxService = mock(OutboxService.class);
    OutboxEventRepository repository = mock(OutboxEventRepository.class);
    OutboxHandler handler = mock(OutboxHandler.class);
    SimpleMeterRegistry meters = new SimpleMeterRegistry();
    OutboxRelay relay;

    OutboxEvent event;

    @BeforeEach
    void setUp() throws Exception {
        given(handler.type()).willReturn(OutboxEventType.AI_REVIEW_REQUEST);
        relay = new OutboxRelay(outboxService, repository, meters, List.of(handler));
        relay.registerMeters();
        event = OutboxEvent.pending(OutboxEventType.AI_REVIEW_REQUEST, 1L, "{}", LocalDateTime.now());
        ReflectionTestUtils.setField(event, "id", 10L);
    }

    @Test
    @DisplayName("처리에 성공하면 SENT로 바꾼다")
    void sent() throws Exception {
        relay.dispatch(event);

        verify(handler).handle(event);
        verify(outboxService).markSent(10L);
        assertThat(count("sent")).isEqualTo(1);
    }

    @Test
    @DisplayName("실패하면 다시 시도하도록 기록하고, 아직 포기하지 않았으면 포기 처리를 하지 않는다")
    void retry() throws Exception {
        doThrow(new RuntimeException("redis down")).when(handler).handle(event);
        given(outboxService.recordFailure(eq(10L), anyString())).willReturn(false);

        relay.dispatch(event);

        verify(outboxService, never()).markSent(any());
        verify(handler, never()).onGiveUp(any(), anyString());
        assertThat(count("retry")).isEqualTo(1);
    }

    @Test
    @DisplayName("최대 시도를 넘기면 포기 처리를 불러 기다리는 쪽을 실패로 끝낸다")
    void giveUp() throws Exception {
        doThrow(new RuntimeException("redis down")).when(handler).handle(event);
        given(outboxService.recordFailure(eq(10L), anyString())).willReturn(true);

        relay.dispatch(event);

        verify(handler).onGiveUp(eq(event), org.mockito.ArgumentMatchers.contains("redis down"));
        assertThat(count("failed")).isEqualTo(1);
    }

    @Test
    @DisplayName("처리기가 없는 종류는 실패로 기록한다")
    void noHandler() {
        OutboxEvent prepare = OutboxEvent.pending(OutboxEventType.AI_REVIEW_PREPARE, 1L, "{}", LocalDateTime.now());
        ReflectionTestUtils.setField(prepare, "id", 11L);

        relay.dispatch(prepare);

        verify(outboxService).recordFailure(eq(11L), org.mockito.ArgumentMatchers.contains("처리기 없음"));
    }

    @Test
    @DisplayName("재시도 간격은 2초부터 두 배씩 늘고 5분을 넘지 않는다")
    void backoff() {
        assertThat(OutboxService.backoff(1)).isEqualTo(Duration.ofSeconds(2));
        assertThat(OutboxService.backoff(3)).isEqualTo(Duration.ofSeconds(8));
        assertThat(OutboxService.backoff(8)).isEqualTo(Duration.ofSeconds(256));
        assertThat(OutboxService.backoff(9)).isEqualTo(Duration.ofMinutes(5));
        assertThat(OutboxService.backoff(30)).isEqualTo(Duration.ofMinutes(5));
    }

    private double count(String result) {
        return meters.counter("outbox.events", "type", "AI_REVIEW_REQUEST", "result", result).count();
    }
}
