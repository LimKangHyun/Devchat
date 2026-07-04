package project.api.domain.chat.chatroom.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import project.api.domain.chat.chatroom.dao.ChatParticipantRepository;
import project.api.domain.chat.chatroom.entity.ChatParticipant;

@ExtendWith(MockitoExtension.class)
class ChatRoomReadServiceTest {

    @InjectMocks
    private ChatRoomReadService chatRoomReadService;

    @Mock private ChatParticipantRepository chatParticipantRepository;
    @Mock private ChatRoomSequenceService chatRoomSequenceService;

    @Nested
    @DisplayName("updateLastReadSequence() - 읽음 처리")
    class UpdateLastReadSequence {

        @Test
        @DisplayName("참가자가 존재하면 lastReadSequence를 업데이트한다")
        void update_participantExists_updatesSequence() {
            ChatParticipant participant = mock(ChatParticipant.class);
            given(chatRoomSequenceService.getLatestSequence(10L)).willReturn(5L);
            given(chatParticipantRepository
                    .findByChatRoomIdAndParticipantIdAndIsActiveTrue(10L, 1L))
                    .willReturn(Optional.of(participant));

            chatRoomReadService.updateLastReadSequence(10L, 1L);

            then(participant).should().updateLastReadSequence(5L);
        }

        @Test
        @DisplayName("참가자가 없으면 업데이트하지 않는다")
        void update_participantNotExists_noUpdate() {
            given(chatRoomSequenceService.getLatestSequence(10L)).willReturn(5L);
            given(chatParticipantRepository
                    .findByChatRoomIdAndParticipantIdAndIsActiveTrue(10L, 1L))
                    .willReturn(Optional.empty());

            chatRoomReadService.updateLastReadSequence(10L, 1L);

            then(chatParticipantRepository).should(never()).save(any());
        }
    }

    @Nested
    @DisplayName("getLatestSequence() - 최신 시퀀스 조회")
    class GetLatestSequence {

        @Test
        @DisplayName("ChatRoomSequenceService 에 위임하여 시퀀스를 반환한다")
        void getLatest_delegatesToSequenceService() {
            given(chatRoomSequenceService.getLatestSequence(10L)).willReturn(5L);

            Long result = chatRoomReadService.getLatestSequence(10L);

            assertThat(result).isEqualTo(5L);
        }
    }
}