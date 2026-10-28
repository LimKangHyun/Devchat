package project.api.domain.chat.chatroom.app;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import project.api.domain.chat.chatmessage.dao.ChatMessageRepository;
import project.api.domain.chat.chatroom.dao.ChatRoomRedisRepository;

import static org.mockito.Mockito.*;

class ChatRoomSequenceServiceTest {

    private final ChatRoomRedisRepository redis = mock(ChatRoomRedisRepository.class);
    private final ChatMessageRepository messages = mock(ChatMessageRepository.class);
    private final ChatRoomSequenceService service = new ChatRoomSequenceService(
            redis, mock(ChatRoomSyncService.class), mock(CheckpointReconstructor.class),
            new SimpleMeterRegistry(), messages);

    @Test
    void 번호가_이미_있으면_그대로_올린다() {
        when(redis.genMessageSeq(1L)).thenReturn(8L);

        service.incrementCache(1L);

        verify(redis, times(1)).genMessageSeq(1L);
        verifyNoInteractions(messages);
    }

    @Test
    void 번호가_없으면_DB_메시지_수로_채운_뒤_올린다() {
        // 새 방이나 Redis가 비워진 뒤: 예전에는 그냥 넘어가 안 읽은 수가 계속 비어 있었다
        when(redis.genMessageSeq(1L)).thenReturn(-1L, 5L);
        when(messages.countByChatRoom_Id(1L)).thenReturn(5L);

        service.incrementCache(1L);

        // 방금 저장된 메시지도 개수에 들어 있으니 하나 적게 채우고, 올리면서 정확히 5가 된다
        verify(redis).setSequenceIfGreater(1L, 4L);
        verify(redis, times(2)).genMessageSeq(1L);
    }
}
