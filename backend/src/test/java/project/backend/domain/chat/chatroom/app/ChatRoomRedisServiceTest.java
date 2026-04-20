package project.backend.domain.chat.chatroom.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
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
import project.backend.global.exception.errorcode.ChatRoomErrorCode;
import project.backend.global.exception.ex.ChatRoomException;

@ExtendWith(MockitoExtension.class)
class ChatRoomRedisServiceTest {

    @InjectMocks
    private ChatRoomRedisService chatRoomRedisService;

    @Mock
    private ChatRoomRedisRepository chatRoomRedisRepository;
    @Mock
    private ChatRoomRepository chatRoomRepository;
    @Mock
    private ChatRoomSyncService chatRoomSyncService;

    private ChatRoom chatRoom;

    @BeforeEach
    void setUp() {
        chatRoom = mock(ChatRoom.class);
    }

    @Nested
    @DisplayName("genMessageSeq() - 메시지 seq 채번")
    class HandleMessageDelivery {

        @Test
        @DisplayName("Redis에서 정상 seq를 반환하면 그대로 반환한다")
        void handleMessageDelivery_normalSeq_returnsAsIs() {
            given(chatRoomRedisRepository.genMessageSeq(10L)).willReturn(5L);

            Long result = chatRoomRedisService.genMessageSeq(10L);

            assertThat(result).isEqualTo(5L);
            then(chatRoomSyncService).should(never()).recoverFromDb(10L);
        }

        @Test
        @DisplayName("Redis가 -1을 반환하면 DB에서 복구 후 dbSeq+1을 반환한다")
        void handleMessageDelivery_minusOne_recoversFromDb() {
            given(chatRoomRedisRepository.genMessageSeq(10L)).willReturn(-1L);
            given(chatRoomSyncService.recoverFromDb(10L)).willReturn(4L);

            Long result = chatRoomRedisService.genMessageSeq(10L);

            assertThat(result).isEqualTo(4L);
        }

        @Test
        @DisplayName("Redis가 -1이고 DB lastSequence가 null이면 1을 반환한다")
        void handleMessageDelivery_minusOne_nullDbSeq_returnsOne() {
            given(chatRoomRedisRepository.genMessageSeq(10L)).willReturn(-1L);
            given(chatRoomSyncService.recoverFromDb(10L)).willReturn(1L);

            Long result = chatRoomRedisService.genMessageSeq(10L);

            assertThat(result).isEqualTo(1L);
        }

        @Test
        @DisplayName("Redis가 -1인데 채팅방이 없으면 예외를 던진다")
        void handleMessageDelivery_minusOne_roomNotFound_throwsException() {
            given(chatRoomRedisRepository.genMessageSeq(10L)).willReturn(-1L);
            given(chatRoomSyncService.recoverFromDb(10L)).willThrow(new ChatRoomException(ChatRoomErrorCode.CHATROOM_NOT_FOUND));

            assertThatThrownBy(() -> chatRoomRedisService.genMessageSeq(10L))
                    .isInstanceOf(ChatRoomException.class);
        }
    }

    @Nested
    @DisplayName("getSortedRoomIds() - 정렬된 방 ID 조회")
    class GetSortedRoomIds {

        @Test
        @DisplayName("roomIds를 그대로 레포지토리에 위임하고 결과를 반환한다")
        void getSortedRoomIds_delegatesToRepository() {
            List<Long> roomIds = List.of(1L, 2L, 3L);
            List<Long> sorted = List.of(3L, 1L, 2L);
            given(chatRoomRedisRepository.getSortedRoomIds(roomIds)).willReturn(sorted);

            List<Long> result = chatRoomRedisService.getSortedRoomIds(roomIds);

            assertThat(result).isEqualTo(sorted);
        }
    }

    @Nested
    @DisplayName("getAndClearUpdatedRooms() - 업데이트된 방 ID 수집 및 초기화")
    class GetAndClearUpdatedRooms {

        @Test
        @DisplayName("레포지토리 결과를 그대로 반환한다")
        void getAndClearUpdatedRooms_delegatesToRepository() {
            Set<String> updatedRooms = Set.of("10", "20");
            given(chatRoomRedisRepository.getAndClearUpdatedRooms()).willReturn(updatedRooms);

            Set<String> result = chatRoomRedisService.getAndClearUpdatedRooms();

            assertThat(result).isEqualTo(updatedRooms);
        }
    }

    @Nested
    @DisplayName("setSequence() - seq 저장")
    class SetSequence {

        @Test
        @DisplayName("roomId와 sequence를 레포지토리에 위임한다")
        void setSequence_delegatesToRepository() {
            chatRoomRedisService.setSequence(10L, 7L);

            then(chatRoomRedisRepository).should().setSequence(10L, 7L);
        }
    }

    @Nested
    @DisplayName("getSequence() - 단일 방 seq 조회")
    class GetSequence {

        @Test
        @DisplayName("레포지토리 결과를 그대로 반환한다")
        void getSequence_delegatesToRepository() {
            given(chatRoomRedisRepository.getSequence(10L)).willReturn(9L);

            Long result = chatRoomRedisService.getSequence(10L);

            assertThat(result).isEqualTo(9L);
        }
    }

    @Nested
    @DisplayName("getSequences() - 복수 방 seq 조회")
    class GetSequences {

        @Test
        @DisplayName("roomIds를 레포지토리에 위임하고 결과를 반환한다")
        void getSequences_delegatesToRepository() {
            List<Long> roomIds = List.of(1L, 2L, 3L);
            List<Long> sequences = List.of(4L, 5L, 6L);
            given(chatRoomRedisRepository.getSequences(roomIds)).willReturn(sequences);

            List<Long> result = chatRoomRedisService.getSequences(roomIds);

            assertThat(result).isEqualTo(sequences);
        }
    }
}