package project.backend.domain.chat.chatroom.app;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import project.backend.domain.chat.chatroom.dao.ChatRoomRedisRepository;
import project.backend.domain.chat.chatroom.dao.ChatRoomRepository;
import project.backend.domain.chat.chatroom.entity.ChatRoom;
import project.backend.global.exception.ex.ChatRoomException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class ChatRoomSyncServiceTest {

    @InjectMocks
    private ChatRoomSyncService chatRoomSyncService;

    @Mock
    private ChatRoomRedisRepository chatRoomRedisRepository;
    @Mock
    private ChatRoomRepository chatRoomRepository;

    @Nested
    @DisplayName("getOrRecoverSeq() - seq 채번")
    class GetOrRecoverSeq {

        @Test
        @DisplayName("Redis에서 정상 seq를 반환하면 그대로 반환한다")
        void getOrRecoverSeq_normalSeq_returnsAsIs() {
            given(chatRoomRedisRepository.genMessageSeq(10L)).willReturn(5L);

            Long result = chatRoomSyncService.getOrRecoverSeq(10L);

            assertThat(result).isEqualTo(5L);
        }

        @Test
        @DisplayName("Redis가 -1을 반환하면 DB에서 복구 후 반환한다")
        void getOrRecoverSeq_minusOne_recoversFromDb() {
            ChatRoom room = mock(ChatRoom.class);
            given(chatRoomRedisRepository.genMessageSeq(10L)).willReturn(-1L);
            given(chatRoomRepository.findById(10L)).willReturn(Optional.of(room));
            given(room.getLastSequence()).willReturn(3L);
            given(chatRoomRedisRepository.recoverAndIncr(10L, 3L)).willReturn(4L);

            Long result = chatRoomSyncService.getOrRecoverSeq(10L);

            assertThat(result).isEqualTo(4L);
        }

        @Test
        @DisplayName("Redis가 -1이고 DB lastSequence가 null이면 1을 반환한다")
        void getOrRecoverSeq_minusOne_nullDbSeq_returnsOne() {
            ChatRoom room = mock(ChatRoom.class);
            given(chatRoomRedisRepository.genMessageSeq(10L)).willReturn(-1L);
            given(chatRoomRepository.findById(10L)).willReturn(Optional.of(room));
            given(room.getLastSequence()).willReturn(0L);
            given(chatRoomRedisRepository.recoverAndIncr(10L, 0L)).willReturn(1L);

            Long result = chatRoomSyncService.getOrRecoverSeq(10L);

            assertThat(result).isEqualTo(1L);
        }

        @Test
        @DisplayName("Redis가 -1인데 채팅방이 없으면 예외를 던진다")
        void getOrRecoverSeq_minusOne_roomNotFound_throwsException() {
            given(chatRoomRedisRepository.genMessageSeq(10L)).willReturn(-1L);
            given(chatRoomRepository.findById(10L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> chatRoomSyncService.getOrRecoverSeq(10L))
                    .isInstanceOf(ChatRoomException.class);
        }
    }
}