package project.api.domain.chat.chatroom.app;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import project.api.domain.chat.chatroom.dao.ChatParticipantRepository;
import project.api.domain.chat.chatroom.event.LeaveChatRoomEvent;
import project.api.domain.chat.chatroom.entity.ChatParticipant;
import project.api.domain.chat.chatroom.entity.ChatRoom;
import project.api.domain.member.entity.Member;
import project.api.global.exception.ex.ChatRoomException;

@ExtendWith(MockitoExtension.class)
class ChatRoomParticipantServiceTest {

    @InjectMocks
    private ChatRoomParticipantService chatRoomParticipantService;

    @Mock private ChatParticipantRepository chatParticipantRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    private Member owner;
    private ChatRoom chatRoom;
    private ChatParticipant ownerParticipant;
    private ChatParticipant memberParticipant;

    @BeforeEach
    void setUp() {
        owner = mock(Member.class);
        chatRoom = mock(ChatRoom.class);
        ownerParticipant = mock(ChatParticipant.class);
        memberParticipant = mock(ChatParticipant.class);
    }

    @Nested
    @DisplayName("handleParticipantJoin() - 참가 처리")
    class HandleParticipantJoin {

        @Test
        @DisplayName("새 멤버는 채팅방에 추가된다")
        void join_newMember_addsParticipant() {
            given(chatRoom.getId()).willReturn(10L);
            Member newMember = mock(Member.class);
            given(newMember.getId()).willReturn(2L);
            given(chatParticipantRepository.findByChatRoomIdAndParticipantId(10L, 2L))
                    .willReturn(Optional.empty());

            chatRoomParticipantService.handleParticipantJoin(chatRoom, newMember);

            then(chatRoom).should().addParticipant(any(ChatParticipant.class));
        }

        @Test
        @DisplayName("이미 활성 참가자가 참가하면 예외를 던진다")
        void join_alreadyActive_throwsException() {
            given(chatRoom.getId()).willReturn(10L);
            given(memberParticipant.isActive()).willReturn(true);
            Member existingMember = mock(Member.class);
            given(existingMember.getId()).willReturn(2L);
            given(chatParticipantRepository.findByChatRoomIdAndParticipantId(10L, 2L))
                    .willReturn(Optional.of(memberParticipant));

            assertThatThrownBy(() -> chatRoomParticipantService.handleParticipantJoin(chatRoom, existingMember))
                    .isInstanceOf(ChatRoomException.class);
        }

        @Test
        @DisplayName("비활성 참가자가 참가하면 rejoin이 호출된다")
        void join_inactiveMember_callsRejoin() {
            given(chatRoom.getId()).willReturn(10L);
            Member rejoiner = mock(Member.class);
            given(rejoiner.getId()).willReturn(2L);
            ChatParticipant inactiveParticipant = mock(ChatParticipant.class);
            given(inactiveParticipant.isActive()).willReturn(false);
            given(chatParticipantRepository.findByChatRoomIdAndParticipantId(10L, 2L))
                    .willReturn(Optional.of(inactiveParticipant));

            chatRoomParticipantService.handleParticipantJoin(chatRoom, rejoiner);

            then(inactiveParticipant).should().rejoin();
            then(chatRoom).should(never()).addParticipant(any());
        }
    }

    @Nested
    @DisplayName("leaveChatRoom() - 채팅방 나가기")
    class LeaveChatRoom {

        @Test
        @DisplayName("일반 참가자는 정상적으로 나간다")
        void leave_normalMember_success() {
            given(memberParticipant.isOwner()).willReturn(false);
            given(chatParticipantRepository.findByChatRoomIdAndParticipantIdAndIsActiveTrue(10L, 2L))
                    .willReturn(Optional.of(memberParticipant));

            chatRoomParticipantService.leaveChatRoom(10L, 2L, "nick");

            then(memberParticipant).should().leave();
            then(eventPublisher).should().publishEvent(any(LeaveChatRoomEvent.class));
        }

        @Test
        @DisplayName("방장이 나가려 하면 예외를 던진다")
        void leave_owner_throwsException() {
            given(ownerParticipant.isOwner()).willReturn(true);
            given(chatParticipantRepository.findByChatRoomIdAndParticipantIdAndIsActiveTrue(10L, 1L))
                    .willReturn(Optional.of(ownerParticipant));

            assertThatThrownBy(() -> chatRoomParticipantService.leaveChatRoom(10L, 1L, "nick"))
                    .isInstanceOf(ChatRoomException.class);
            then(ownerParticipant).should(never()).leave();
        }

        @Test
        @DisplayName("참가자가 아니면 예외를 던진다")
        void leave_notParticipant_throwsException() {
            given(chatParticipantRepository.findByChatRoomIdAndParticipantIdAndIsActiveTrue(10L, 99L))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> chatRoomParticipantService.leaveChatRoom(10L, 99L, "nick"))
                    .isInstanceOf(ChatRoomException.class);
        }
    }

    @Nested
    @DisplayName("validateParticipant() - 참가자 검증")
    class ValidateParticipant {

        @Test
        @DisplayName("활성 참가자이면 예외 없이 통과한다")
        void validate_active_noException() {
            given(chatParticipantRepository.existsByParticipantIdAndChatRoomIdAndIsActiveTrue(1L, 10L))
                    .willReturn(true);

            chatRoomParticipantService.validateParticipant(1L, 10L);
        }

        @Test
        @DisplayName("참가자가 아니면 예외를 던진다")
        void validate_notMember_throwsException() {
            given(chatParticipantRepository.existsByParticipantIdAndChatRoomIdAndIsActiveTrue(99L, 10L))
                    .willReturn(false);

            assertThatThrownBy(() -> chatRoomParticipantService.validateParticipant(99L, 10L))
                    .isInstanceOf(ChatRoomException.class);
        }
    }
}