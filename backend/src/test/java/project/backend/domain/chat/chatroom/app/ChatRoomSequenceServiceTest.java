package project.backend.domain.chat.chatroom.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import project.backend.domain.chat.chatroom.dao.ChatParticipantRepository;
import project.backend.domain.chat.chatroom.dao.ChatRoomRepository;
import project.backend.domain.chat.chatroom.entity.ChatRoom;

@ExtendWith(MockitoExtension.class)
class ChatRoomSequenceServiceTest {

    @InjectMocks
    private ChatRoomSequenceService chatRoomSequenceService;

    @Mock private ChatRoomRepository chatRoomRepository;
    @Mock private ChatParticipantRepository chatParticipantRepository;
    @Mock private ChatRoomCacheService chatRoomCacheService;
    @Mock private ChatRoomRedisService chatRoomRedisService;

    @Nested
    @DisplayName("syncSequencesToDb() - Redis → DB 시퀀스 동기화")
    class SyncSequencesToDb {

        @Test
        @DisplayName("업데이트된 방이 없으면 조기 반환한다")
        void sync_empty_earlyReturn() {
            given(chatRoomRedisService.getAndClearUpdatedRooms()).willReturn(Collections.emptySet());

            chatRoomSequenceService.syncSequencesToDb();

            then(chatRoomRepository).should(never()).findAllById(any());
        }

        @Test
        @DisplayName("Redis 시퀀스가 DB보다 크면 DB를 업데이트한다")
        void sync_redisHigher_updatesDb() {
            ChatRoom chatRoom = mock(ChatRoom.class);
            given(chatRoom.getId()).willReturn(10L);
            given(chatRoom.getLastSequence()).willReturn(3L);

            given(chatRoomRedisService.getAndClearUpdatedRooms()).willReturn(Set.of("10"));
            given(chatRoomRepository.findAllById(List.of(10L))).willReturn(List.of(chatRoom));
            given(chatRoomRedisService.getSequences(List.of(10L))).willReturn(List.of(7L));

            chatRoomSequenceService.syncSequencesToDb();

            then(chatRoom).should().updateLastSequence(7L);
        }

        @Test
        @DisplayName("Redis 시퀀스가 DB보다 작거나 같으면 업데이트하지 않는다")
        void sync_redisNotHigher_noUpdate() {
            ChatRoom chatRoom = mock(ChatRoom.class);
            given(chatRoom.getId()).willReturn(10L);
            given(chatRoom.getLastSequence()).willReturn(10L);

            given(chatRoomRedisService.getAndClearUpdatedRooms()).willReturn(Set.of("10"));
            given(chatRoomRepository.findAllById(List.of(10L))).willReturn(List.of(chatRoom));
            given(chatRoomRedisService.getSequences(List.of(10L))).willReturn(List.of(5L));

            chatRoomSequenceService.syncSequencesToDb();

            then(chatRoom).should(never()).updateLastSequence(anyLong());
        }
    }

    @Nested
    @DisplayName("getLatestSequence() - 최신 시퀀스 조회")
    class GetLatestSequence {

        @Test
        @DisplayName("Redis에 시퀀스가 있으면 Redis 값을 반환한다")
        void getLatest_fromRedis_returnsRedisValue() {
            given(chatRoomCacheService.getLatestSequence(10L)).willReturn(5L);

            Long result = chatRoomSequenceService.getLatestSequence(10L);

            assertThat(result).isEqualTo(5L);
        }
    }
}