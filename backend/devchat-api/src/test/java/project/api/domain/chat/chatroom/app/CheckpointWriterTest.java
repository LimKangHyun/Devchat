package project.api.domain.chat.chatroom.app;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import project.api.domain.chat.chatmessage.dao.ChatMessageRepository;
import project.api.domain.chat.chatroom.dao.ChatRoomCheckpointRepository;
import project.api.domain.chat.chatroom.dao.ChatRoomRedisRepository;
import project.api.domain.chat.chatroom.entity.ChatRoomCheckpoint;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("CheckpointWriter - checkpoint 갱신")
class CheckpointWriterTest {

    @InjectMocks
    private CheckpointWriter checkpointWriter;

    @Mock private ChatRoomCheckpointRepository checkpointRepository;
    @Mock private ChatMessageRepository chatMessageRepository;
    @Mock private ChatRoomRedisRepository chatRoomRedisRepository;

    private ChatRoomCheckpoint checkpointWith(long syncedId, long cumulative) {
        ChatRoomCheckpoint cp = new ChatRoomCheckpoint(10L);
        cp.advance(syncedId, cumulative);   // 초기 상태 세팅
        return cp;
    }

    @Nested
    @DisplayName("델타 계산")
    class DeltaCalculation {

        @Test
        @DisplayName("체크포인트 이후 안전 워터마크까지 신규 메시지가 있으면 델타만큼 누적하고 동기화 지점을 전진시킨다")
        void reconstruct_newMessages_advancesByDelta() {
            ChatRoomCheckpoint cp = checkpointWith(850L, 100L);
            given(checkpointRepository.findByRoomIdForUpdate(10L)).willReturn(Optional.of(cp));
            given(chatMessageRepository.findMaxIdByRoomIdAndCreatedBefore(any(), any(LocalDateTime.class)))
                    .willReturn(858L);
            given(chatMessageRepository.countByChatRoom_IdAndIdGreaterThanAndIdLessThanEqual(10L, 850L, 858L))
                    .willReturn(8L);

            Long result = checkpointWriter.reconstruct(10L);

            assertThat(result).isEqualTo(108L);
            assertThat(cp.getSyncedMessageId()).isEqualTo(858L);
            assertThat(cp.getCumulativeCount()).isEqualTo(108L);
        }

        @Test
        @DisplayName("전체 COUNT가 아니라 체크포인트~안전 워터마크 범위만 집계한다")
        void reconstruct_countsOnlyDeltaRange() {
            ChatRoomCheckpoint cp = checkpointWith(850L, 100L);
            given(checkpointRepository.findByRoomIdForUpdate(10L)).willReturn(Optional.of(cp));
            given(chatMessageRepository.findMaxIdByRoomIdAndCreatedBefore(any(), any(LocalDateTime.class)))
                    .willReturn(858L);
            given(chatMessageRepository.countByChatRoom_IdAndIdGreaterThanAndIdLessThanEqual(10L, 850L, 858L))
                    .willReturn(8L);

            checkpointWriter.reconstruct(10L);

            // syncedId(850) 초과 ~ safeMaxId(858) 이하 범위로만 집계했는지 검증.
            // 상한(858)이 빠지면 워터마크 너머까지 세게 되어 다음 실행에서 중복 집계된다.
            then(chatMessageRepository).should()
                    .countByChatRoom_IdAndIdGreaterThanAndIdLessThanEqual(10L, 850L, 858L);
        }

        @Test
        @DisplayName("이미 최신이면 COUNT 쿼리를 실행하지 않는다")
        void reconstruct_alreadySynced_skipsCount() {
            ChatRoomCheckpoint cp = checkpointWith(858L, 108L);
            given(checkpointRepository.findByRoomIdForUpdate(10L)).willReturn(Optional.of(cp));
            given(chatMessageRepository.findMaxIdByRoomIdAndCreatedBefore(any(), any(LocalDateTime.class)))
                    .willReturn(858L);

            Long result = checkpointWriter.reconstruct(10L);

            assertThat(result).isEqualTo(108L);
            then(chatMessageRepository).should(never())
                    .countByChatRoom_IdAndIdGreaterThanAndIdLessThanEqual(anyLong(), anyLong(), anyLong());
        }

        @Test
        @DisplayName("메시지가 하나도 없는 방은 0을 반환한다")
        void reconstruct_emptyRoom_returnsZero() {
            ChatRoomCheckpoint cp = new ChatRoomCheckpoint(10L);
            given(checkpointRepository.findByRoomIdForUpdate(10L)).willReturn(Optional.of(cp));
            given(chatMessageRepository.findMaxIdByRoomIdAndCreatedBefore(any(), any(LocalDateTime.class)))
                    .willReturn(null);

            Long result = checkpointWriter.reconstruct(10L);

            assertThat(result).isZero();
            then(chatMessageRepository).should(never())
                    .countByChatRoom_IdAndIdGreaterThanAndIdLessThanEqual(anyLong(), anyLong(), anyLong());
        }

        @Test
        @DisplayName("워터마크 지연 구간(lag 이내) 메시지는 아직 집계 대상에 포함되지 않는다")
        void reconstruct_recentMessages_notYetCounted() {
            // safeMaxId가 syncedId를 넘지 못하면(= lag 이내 메시지만 새로 쌓인 경우)
            // advance 자체가 스킵된다. 이게 "늦은 커밋을 기다려주는" 지연의 실제 동작이다.
            ChatRoomCheckpoint cp = checkpointWith(850L, 100L);
            given(checkpointRepository.findByRoomIdForUpdate(10L)).willReturn(Optional.of(cp));
            given(chatMessageRepository.findMaxIdByRoomIdAndCreatedBefore(any(), any(LocalDateTime.class)))
                    .willReturn(850L); // threshold 이전 기준으로는 아직 850에 머물러 있음

            Long result = checkpointWriter.reconstruct(10L);

            assertThat(result).isEqualTo(100L);
            assertThat(cp.getSyncedMessageId()).isEqualTo(850L);
            then(chatMessageRepository).should(never())
                    .countByChatRoom_IdAndIdGreaterThanAndIdLessThanEqual(anyLong(), anyLong(), anyLong());
        }
    }

    @Nested
    @DisplayName("체크포인트 초기화")
    class Initialization {

        @Test
        @DisplayName("체크포인트가 없으면 생성 후 안전 워터마크까지 전체 메시지를 집계한다")
        void reconstruct_noCheckpoint_createsAndCounts() {
            ChatRoomCheckpoint created = new ChatRoomCheckpoint(10L);
            given(checkpointRepository.findByRoomIdForUpdate(10L)).willReturn(Optional.empty());
            given(checkpointRepository.save(any(ChatRoomCheckpoint.class))).willReturn(created);
            given(chatMessageRepository.findMaxIdByRoomIdAndCreatedBefore(any(), any(LocalDateTime.class)))
                    .willReturn(50L);
            given(chatMessageRepository.countByChatRoom_IdAndIdGreaterThanAndIdLessThanEqual(10L, 0L, 50L))
                    .willReturn(50L);

            Long result = checkpointWriter.reconstruct(10L);

            assertThat(result).isEqualTo(50L);
        }
    }

    @Nested
    @DisplayName("Redis 장애 격리")
    class RedisFailureIsolation {

        @Test
        @DisplayName("Redis 캐시 갱신이 실패해도 checkpoint 갱신은 정상 완료된다")
        void reconstruct_redisFails_stillReturnsCorrectValue() {
            ChatRoomCheckpoint cp = checkpointWith(850L, 100L);
            given(checkpointRepository.findByRoomIdForUpdate(10L)).willReturn(Optional.of(cp));
            given(chatMessageRepository.findMaxIdByRoomIdAndCreatedBefore(any(), any(LocalDateTime.class)))
                    .willReturn(858L);
            given(chatMessageRepository.countByChatRoom_IdAndIdGreaterThanAndIdLessThanEqual(10L, 850L, 858L))
                    .willReturn(8L);
            doThrow(new RuntimeException("Redis down"))
                    .when(chatRoomRedisRepository).setSequenceIfGreater(anyLong(), anyLong());

            Long result = checkpointWriter.reconstruct(10L);

            assertThat(result).isEqualTo(108L);
            assertThat(cp.getCumulativeCount()).isEqualTo(108L);
        }
    }

    @Nested
    @DisplayName("캐시 갱신")
    class CacheUpdate {

        @Test
        @DisplayName("재구성 결과를 역행 방지 방식으로 캐시에 반영한다")
        void reconstruct_updatesCache_withOverwriteProtection() {
            ChatRoomCheckpoint cp = checkpointWith(850L, 100L);
            given(checkpointRepository.findByRoomIdForUpdate(10L)).willReturn(Optional.of(cp));
            given(chatMessageRepository.findMaxIdByRoomIdAndCreatedBefore(any(), any(LocalDateTime.class)))
                    .willReturn(858L);
            given(chatMessageRepository.countByChatRoom_IdAndIdGreaterThanAndIdLessThanEqual(10L, 850L, 858L))
                    .willReturn(8L);

            checkpointWriter.reconstruct(10L);

            then(chatRoomRedisRepository).should().setSequenceIfGreater(10L, 108L);
        }
    }
}