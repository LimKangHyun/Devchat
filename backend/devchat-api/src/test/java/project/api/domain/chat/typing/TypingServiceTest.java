package project.api.domain.chat.typing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import project.api.domain.chat.chatroom.dao.ChatParticipantRepository;

class TypingServiceTest {

    private final SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);
    private final ChatParticipantRepository participants = mock(ChatParticipantRepository.class);
    private final AtomicLong now = new AtomicLong(1_000_000);
    private final TypingService service = new TypingService(template, participants, now::get);

    @Test
    void 참여자의_입력_중_신호를_방_구독자에게_전달한다() {
        when(participants.existsByParticipantIdAndChatRoomIdAndIsActiveTrue(1L, 10L)).thenReturn(true);

        service.signal(1L, "강현", 10L, true);

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(template).convertAndSend(eq("/topic/chat/10/typing"), payload.capture());
        assertThat(payload.getValue()).isEqualTo(new TypingService.TypingEvent(1L, "강현", true));
    }

    @Test
    void 참여하지_않은_방에는_보내지_않는다() {
        when(participants.existsByParticipantIdAndChatRoomIdAndIsActiveTrue(1L, 10L)).thenReturn(false);

        service.signal(1L, "강현", 10L, true);

        verify(template, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void 입력_중은_2초에_한_번만_전달하고_참여_확인은_기억한다() {
        when(participants.existsByParticipantIdAndChatRoomIdAndIsActiveTrue(1L, 10L)).thenReturn(true);

        service.signal(1L, "강현", 10L, true);
        now.addAndGet(500);
        service.signal(1L, "강현", 10L, true);   // 너무 잦음 → 버림
        now.addAndGet(2_000);
        service.signal(1L, "강현", 10L, true);   // 다시 전달

        verify(template, times(2)).convertAndSend(eq("/topic/chat/10/typing"), any(Object.class));
        verify(participants, times(1)).existsByParticipantIdAndChatRoomIdAndIsActiveTrue(1L, 10L);
    }

    @Test
    void 멈춤_신호는_입력_중을_알린_뒤에만_한_번_전달한다() {
        when(participants.existsByParticipantIdAndChatRoomIdAndIsActiveTrue(1L, 10L)).thenReturn(true);

        service.signal(1L, "강현", 10L, false);  // 알린 적 없음 → 버림
        service.signal(1L, "강현", 10L, true);
        now.addAndGet(400);
        service.signal(1L, "강현", 10L, false);  // 전달
        now.addAndGet(400);
        service.signal(1L, "강현", 10L, false);  // 이미 멈춤 → 버림

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(template, times(2)).convertAndSend(eq("/topic/chat/10/typing"), payload.capture());
        assertThat(payload.getAllValues()).containsExactly(
                new TypingService.TypingEvent(1L, "강현", true),
                new TypingService.TypingEvent(1L, "강현", false));
    }

    @Test
    void 오래_조용한_기록은_지운다() {
        when(participants.existsByParticipantIdAndChatRoomIdAndIsActiveTrue(1L, 10L)).thenReturn(true);
        service.signal(1L, "강현", 10L, true);
        assertThat(service.trackedCount()).isEqualTo(1);

        now.addAndGet(61_000);
        service.cleanUp();

        assertThat(service.trackedCount()).isZero();
    }
}
