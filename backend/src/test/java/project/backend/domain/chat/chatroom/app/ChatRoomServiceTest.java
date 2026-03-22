package project.backend.domain.chat.chatroom.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import project.backend.domain.chat.chatmessage.dao.ChatMessageRepository;
import project.backend.domain.chat.chatmessage.entity.ChatMessage;
import project.backend.domain.chat.chatmessage.mapper.ChatMessageMapper;
import project.backend.domain.chat.chatroom.dao.ChatParticipantRepository;
import project.backend.domain.chat.chatroom.dao.ChatRoomAlarmRepository;
import project.backend.domain.chat.chatroom.dao.ChatRoomRepository;
import project.backend.domain.chat.chatroom.dao.ChatRoomWithSequenceProjection;
import project.backend.domain.chat.chatroom.dto.ChatRoomRequest;
import project.backend.domain.chat.chatroom.dto.ChatRoomSimpleResponse;
import project.backend.domain.chat.chatroom.dto.EntryRoomResponse;
import project.backend.domain.chat.chatroom.dto.InviteJoinResponse;
import project.backend.domain.chat.chatroom.dto.AllRoomsResponse;
import project.backend.domain.chat.chatroom.dto.event.DeleteChatRoomEvent;
import project.backend.domain.chat.chatroom.dto.event.JoinChatRoomEvent;
import project.backend.domain.chat.chatroom.dto.event.LeaveChatRoomEvent;
import project.backend.domain.chat.chatroom.entity.ChatParticipant;
import project.backend.domain.chat.chatroom.entity.ChatRoom;
import project.backend.domain.chat.chatroom.entity.ChatRoomAlarm;
import project.backend.domain.chat.chatroom.mapper.ChatRoomMapper;
import project.backend.domain.chat.github.app.GitMessageService;
import project.backend.domain.community.dao.PostRepository;
import project.backend.domain.member.app.MemberService;
import project.backend.domain.member.entity.Member;
import project.backend.global.exception.errorcode.ChatRoomErrorCode;
import project.backend.global.exception.ex.ChatRoomException;

@ExtendWith(MockitoExtension.class)
class ChatRoomServiceTest {

    @InjectMocks
    private ChatRoomService chatRoomService;

    @Mock
    private PostRepository postRepository;
    @Mock
    private ChatRoomRepository chatRoomRepository;
    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private ChatRoomAlarmRepository chatRoomAlarmRepository;
    @Mock
    private ChatParticipantRepository chatParticipantRepository;
    @Mock
    private ChatRoomMapper chatRoomMapper;
    @Mock
    private ChatMessageMapper chatMessageMapper;
    @Mock
    private MemberService memberService;
    @Mock
    private GitMessageService gitMessageService;
    @Mock
    private ChatRoomRedisService chatRoomRedisService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private MeterRegistry meterRegistry;

    private Member owner;
    private ChatRoom chatRoom;
    private ChatParticipant ownerParticipant;
    private ChatParticipant memberParticipant;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(chatRoomService, "githubUsername", "github-bot");

        owner = mock(Member.class);
        given(owner.getId()).willReturn(1L);
        given(owner.getNickname()).willReturn("ownerNick");

        chatRoom = mock(ChatRoom.class);
        given(chatRoom.getId()).willReturn(10L);
        given(chatRoom.getName()).willReturn("테스트 방");
        given(chatRoom.getInviteCode()).willReturn("INVITE-CODE");

        ownerParticipant = mock(ChatParticipant.class);
        given(ownerParticipant.isOwner()).willReturn(true);
        given(ownerParticipant.isActive()).willReturn(true);
        given(ownerParticipant.getParticipant()).willReturn(owner);

        memberParticipant = mock(ChatParticipant.class);
        given(memberParticipant.isOwner()).willReturn(false);
        given(memberParticipant.isActive()).willReturn(true);
    }

    @Nested
    @DisplayName("createChatRoom() - 채팅방 생성")
    class CreateChatRoom {

        @Test
        @DisplayName("레포지토리 URL 없이 채팅방을 생성하면 웹훅 등록 없이 방이 생성된다")
        void createChatRoom_noRepository_success() {
            // given
            ChatRoomRequest request = mock(ChatRoomRequest.class);
            given(request.getRepositoryUrl()).willReturn("");

            given(memberService.getMemberById(1L)).willReturn(owner);
            given(chatRoomMapper.toEntity(request)).willReturn(chatRoom);
            given(chatRoomRepository.save(chatRoom)).willReturn(chatRoom);

            ChatRoomSimpleResponse expected = mock(ChatRoomSimpleResponse.class);
            given(chatRoomMapper.toSimpleResponse(chatRoom, owner)).willReturn(expected);

            ChatRoomAlarm alarm = mock(ChatRoomAlarm.class);
            given(chatRoomAlarmRepository.save(any())).willReturn(alarm);

            // when
            ChatRoomSimpleResponse result = chatRoomService.createChatRoom(request, 1L);

            // then
            assertThat(result).isEqualTo(expected);
            then(gitMessageService).should(never())
                .registerWebhook(anyString(), anyLong(), anyLong());
        }

        @Test
        @DisplayName("레포지토리 URL이 있으면 웹훅 등록과 깃허브봇 참가가 실행된다")
        void createChatRoom_withRepository_registersWebhookAndBot() {
            // given
            ChatRoomRequest request = mock(ChatRoomRequest.class);
            given(request.getRepositoryUrl()).willReturn("https://github.com/test/repo");

            Member githubBot = mock(Member.class);
            given(memberService.getMemberById(1L)).willReturn(owner);
            given(memberService.getMemberByUsername("github-bot")).willReturn(githubBot);
            given(chatRoomMapper.toEntity(request)).willReturn(chatRoom);
            given(chatRoomRepository.save(chatRoom)).willReturn(chatRoom);
            given(chatRoomMapper.toSimpleResponse(chatRoom, owner)).willReturn(
                mock(ChatRoomSimpleResponse.class));
            given(chatRoomAlarmRepository.save(any())).willReturn(mock(ChatRoomAlarm.class));

            // when
            chatRoomService.createChatRoom(request, 1L);

            // then
            then(gitMessageService).should()
                .registerWebhook("https://github.com/test/repo", 10L, 1L);
            then(chatRoom).should().addParticipant(any(ChatParticipant.class));
        }
    }

    @Nested
    @DisplayName("joinChatRoom() - 초대 코드로 채팅방 참가")
    class JoinChatRoom {

        @Test
        @DisplayName("새 멤버가 정상적으로 채팅방에 참가한다")
        void joinChatRoom_newMember_success() {
            // given
            Member joiner = mock(Member.class);
            given(joiner.getId()).willReturn(2L);
            given(joiner.getNickname()).willReturn("joinerNick");

            given(chatRoomRepository.findByInviteCode("INVITE-CODE")).willReturn(
                Optional.of(chatRoom));
            given(memberService.getMemberById(2L)).willReturn(joiner);
            given(chatParticipantRepository.findByChatRoomIdAndParticipantId(10L, 2L))
                .willReturn(Optional.empty());
            given(chatRoomRedisService.handleMessageDelivery(10L)).willReturn(1L);
            given(chatRoomAlarmRepository.save(any())).willReturn(mock(ChatRoomAlarm.class));

            ChatMessage joinMessage = mock(ChatMessage.class);
            given(joinMessage.getId()).willReturn(500L);
            given(joinMessage.getSendAt()).willReturn(java.time.LocalDateTime.now());
            given(chatMessageMapper.toEntityWithJoinEvent(eq(chatRoom), eq(joiner), any(), eq(1L)))
                .willReturn(joinMessage);
            given(chatMessageRepository.save(joinMessage)).willReturn(joinMessage);

            // when
            InviteJoinResponse result = chatRoomService.joinChatRoom("INVITE-CODE", 2L);

            // then
            assertThat(result.getId()).isEqualTo(10L);
            assertThat(result.getInviteCode()).isEqualTo("INVITE-CODE");
            then(eventPublisher).should().publishEvent(any(JoinChatRoomEvent.class));
        }

        @Test
        @DisplayName("이미 활성 참가자가 다시 참가하면 ALREADY_PARTICIPANT 예외를 던진다")
        void joinChatRoom_alreadyActive_throwsException() {
            // given
            Member joiner = mock(Member.class);
            given(joiner.getId()).willReturn(2L);

            given(chatRoomRepository.findByInviteCode("INVITE-CODE")).willReturn(
                Optional.of(chatRoom));
            given(memberService.getMemberById(2L)).willReturn(joiner);

            ChatParticipant activeParticipant = mock(ChatParticipant.class);
            given(activeParticipant.isActive()).willReturn(true);
            given(chatParticipantRepository.findByChatRoomIdAndParticipantId(10L, 2L))
                .willReturn(Optional.of(activeParticipant));

            // when & then
            assertThatThrownBy(() -> chatRoomService.joinChatRoom("INVITE-CODE", 2L))
                .isInstanceOf(ChatRoomException.class);
        }

        @Test
        @DisplayName("비활성 참가자가 재참가하면 rejoin()이 호출된다")
        void joinChatRoom_inactiveMember_callsRejoin() {
            // given
            Member rejoiner = mock(Member.class);
            given(rejoiner.getId()).willReturn(2L);
            given(rejoiner.getNickname()).willReturn("rejoinerNick");

            ChatParticipant inactiveParticipant = mock(ChatParticipant.class);
            given(inactiveParticipant.isActive()).willReturn(false);

            given(chatRoomRepository.findByInviteCode("INVITE-CODE")).willReturn(
                Optional.of(chatRoom));
            given(memberService.getMemberById(2L)).willReturn(rejoiner);
            given(chatParticipantRepository.findByChatRoomIdAndParticipantId(10L, 2L))
                .willReturn(Optional.of(inactiveParticipant));
            given(chatRoomRedisService.handleMessageDelivery(10L)).willReturn(2L);
            given(chatRoomAlarmRepository.save(any())).willReturn(mock(ChatRoomAlarm.class));

            ChatMessage joinMessage = mock(ChatMessage.class);
            given(joinMessage.getId()).willReturn(501L);
            given(joinMessage.getSendAt()).willReturn(java.time.LocalDateTime.now());
            given(
                chatMessageMapper.toEntityWithJoinEvent(eq(chatRoom), eq(rejoiner), any(), eq(2L)))
                .willReturn(joinMessage);
            given(chatMessageRepository.save(joinMessage)).willReturn(joinMessage);

            // when
            chatRoomService.joinChatRoom("INVITE-CODE", 2L);

            // then
            then(inactiveParticipant).should().rejoin();
            then(chatRoom).should(never()).addParticipant(any());
        }

        @Test
        @DisplayName("존재하지 않는 초대 코드로 참가하면 예외를 던진다")
        void joinChatRoom_invalidCode_throwsException() {
            // given
            given(chatRoomRepository.findByInviteCode("WRONG-CODE")).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> chatRoomService.joinChatRoom("WRONG-CODE", 2L))
                .isInstanceOf(ChatRoomException.class);
        }
    }

    @Nested
    @DisplayName("leaveChatRoom() - 채팅방 나가기")
    class LeaveChatRoom {

        @Test
        @DisplayName("일반 참가자는 채팅방을 정상적으로 나간다")
        void leaveChatRoom_normalMember_success() {
            // given
            Member leavingMember = mock(Member.class);
            given(leavingMember.getNickname()).willReturn("leaverNick");

            given(
                chatParticipantRepository.findByChatRoomIdAndParticipantIdAndIsActiveTrue(10L, 2L))
                .willReturn(Optional.of(memberParticipant));
            given(memberService.getMemberById(2L)).willReturn(leavingMember);

            // findTopByParticipantId... → 가장 최근 활성 방 없음
            given(chatParticipantRepository.findTopByParticipantIdAndIsActiveTrueOrderByJoinAtDesc(
                2L))
                .willReturn(Optional.empty());

            // when
            chatRoomService.leaveChatRoom(10L, 2L);

            // then
            then(memberParticipant).should().leave();
            then(eventPublisher).should().publishEvent(any(LeaveChatRoomEvent.class));
            then(leavingMember).should().setRecentRoomId(null);
        }

        @Test
        @DisplayName("방 나가기 후 남은 활성 방이 있으면 recentRoomId를 그 방으로 업데이트한다")
        void leaveChatRoom_hasOtherRoom_updatesRecentRoom() {
            // given
            Member leavingMember = mock(Member.class);
            given(leavingMember.getNickname()).willReturn("leaverNick");

            ChatParticipant otherRoomParticipant = mock(ChatParticipant.class);
            ChatRoom otherRoom = mock(ChatRoom.class);
            given(otherRoom.getId()).willReturn(20L);
            given(otherRoomParticipant.getChatRoom()).willReturn(otherRoom);

            given(
                chatParticipantRepository.findByChatRoomIdAndParticipantIdAndIsActiveTrue(10L, 2L))
                .willReturn(Optional.of(memberParticipant));
            given(memberService.getMemberById(2L)).willReturn(leavingMember);
            given(chatParticipantRepository.findTopByParticipantIdAndIsActiveTrueOrderByJoinAtDesc(
                2L))
                .willReturn(Optional.of(otherRoomParticipant));

            // when
            chatRoomService.leaveChatRoom(10L, 2L);

            // then
            then(leavingMember).should().setRecentRoomId(20L);
        }

        @Test
        @DisplayName("방장이 채팅방을 나가려 하면 OWNER_CANNOT_LEAVE 예외를 던진다")
        void leaveChatRoom_owner_throwsException() {
            // given
            given(
                chatParticipantRepository.findByChatRoomIdAndParticipantIdAndIsActiveTrue(10L, 1L))
                .willReturn(Optional.of(ownerParticipant));

            // when & then
            assertThatThrownBy(() -> chatRoomService.leaveChatRoom(10L, 1L))
                .isInstanceOf(ChatRoomException.class);
            then(memberParticipant).should(never()).leave();
        }

        @Test
        @DisplayName("참가하지 않은 멤버가 나가기를 시도하면 예외를 던진다")
        void leaveChatRoom_notParticipant_throwsException() {
            // given
            given(
                chatParticipantRepository.findByChatRoomIdAndParticipantIdAndIsActiveTrue(10L, 99L))
                .willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> chatRoomService.leaveChatRoom(10L, 99L))
                .isInstanceOf(ChatRoomException.class);
        }
    }

    @Nested
    @DisplayName("deleteChatRoom() - 채팅방 삭제")
    class DeleteChatRoom {

        @Test
        @DisplayName("방장이 채팅방을 삭제하면 관련 데이터가 모두 삭제된다")
        void deleteChatRoom_owner_success() {
            // given
            given(chatRoomRepository.findById(10L)).willReturn(Optional.of(chatRoom));
            given(
                chatParticipantRepository.findByChatRoomIdAndParticipantIdAndIsActiveTrue(10L, 1L))
                .willReturn(Optional.of(ownerParticipant));

            // when
            chatRoomService.deleteChatRoom(10L, 1L);

            // then
            then(gitMessageService).should().deleteWebhook(chatRoom, 1L);
            then(eventPublisher).should().publishEvent(any(DeleteChatRoomEvent.class));
            then(chatParticipantRepository).should().deleteByChatRoom_Id(10L);
            then(chatMessageRepository).should().deleteByChatRoom_Id(10L);
            then(postRepository).should().deleteByChatRoom_Id(10L);
            then(chatRoomRepository).should().delete(chatRoom);
        }

        @Test
        @DisplayName("일반 멤버가 삭제를 시도하면 OWNER_PERMISSION_REQUIRED 예외를 던진다")
        void deleteChatRoom_notOwner_throwsException() {
            // given
            given(chatRoomRepository.findById(10L)).willReturn(Optional.of(chatRoom));
            given(
                chatParticipantRepository.findByChatRoomIdAndParticipantIdAndIsActiveTrue(10L, 2L))
                .willReturn(Optional.of(memberParticipant));

            // when & then
            assertThatThrownBy(() -> chatRoomService.deleteChatRoom(10L, 2L))
                .isInstanceOf(ChatRoomException.class);
            then(chatRoomRepository).should(never()).delete(any());
        }

        @Test
        @DisplayName("존재하지 않는 채팅방을 삭제하면 예외를 던진다")
        void deleteChatRoom_roomNotFound_throwsException() {
            // given
            given(chatRoomRepository.findById(999L)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> chatRoomService.deleteChatRoom(999L, 1L))
                .isInstanceOf(ChatRoomException.class);
        }
    }

    @Nested
    @DisplayName("toggleAlarm() - 알람 토글")
    class ToggleAlarm {

        @Test
        @DisplayName("알람이 켜져 있으면 끄고 false를 반환한다")
        void toggleAlarm_enabled_returnsDisabled() {
            // given
            ChatRoomAlarm alarm = mock(ChatRoomAlarm.class);
            given(alarm.isEnabled()).willReturn(false); // toggle 후 반환값
            given(chatRoomAlarmRepository.findByIdMemberIdAndIdRoomId(1L, 10L))
                .willReturn(Optional.of(alarm));

            // when
            boolean result = chatRoomService.toggleAlarm(10L, 1L);

            // then
            assertThat(result).isFalse();
            then(alarm).should().setEnabled(true); // !false = true → setEnabled(true)
        }

        @Test
        @DisplayName("알람이 꺼져 있으면 켜고 true를 반환한다")
        void toggleAlarm_disabled_returnsEnabled() {
            // given
            ChatRoomAlarm alarm = mock(ChatRoomAlarm.class);
            given(alarm.isEnabled()).willReturn(true);
            given(chatRoomAlarmRepository.findByIdMemberIdAndIdRoomId(1L, 10L))
                .willReturn(Optional.of(alarm));

            // when
            boolean result = chatRoomService.toggleAlarm(10L, 1L);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("알람 정보가 없으면 ALARM_NOT_FOUND 예외를 던진다")
        void toggleAlarm_notFound_throwsException() {
            // given
            given(chatRoomAlarmRepository.findByIdMemberIdAndIdRoomId(1L, 10L))
                .willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> chatRoomService.toggleAlarm(10L, 1L))
                .isInstanceOf(ChatRoomException.class);
        }
    }

    @Nested
    @DisplayName("validateParticipant() - 참가자 검증")
    class ValidateParticipant {

        @Test
        @DisplayName("활성 참가자이면 예외 없이 통과한다")
        void validateParticipant_active_noException() {
            // given
            given(chatParticipantRepository.existsByParticipantIdAndChatRoomIdAndIsActiveTrue(1L,
                10L))
                .willReturn(true);

            // when & then (no exception)
            chatRoomService.validateParticipant(1L, 10L);
        }

        @Test
        @DisplayName("참가자가 아니면 NOT_PARTICIPANT 예외를 던진다")
        void validateParticipant_notMember_throwsException() {
            // given
            given(chatParticipantRepository.existsByParticipantIdAndChatRoomIdAndIsActiveTrue(99L,
                10L))
                .willReturn(false);

            // when & then
            assertThatThrownBy(() -> chatRoomService.validateParticipant(99L, 10L))
                .isInstanceOf(ChatRoomException.class);
        }
    }

    @Nested
    @DisplayName("getLatestSequence() - 최신 시퀀스 조회")
    class GetLatestSequence {

        @Test
        @DisplayName("Redis에 시퀀스가 있으면 Redis 값을 반환한다")
        void getLatestSequence_fromRedis_returnsRedisValue() {
            // given
            given(chatRoomRedisService.getSequence(10L)).willReturn(5L);

            // when
            Long result = chatRoomService.getLatestSequence(10L);

            // then
            assertThat(result).isEqualTo(5L);
            then(chatRoomRepository).should(never()).findById(anyLong());
        }

        @Test
        @DisplayName("Redis 시퀀스가 0이면 DB에서 조회 후 Redis에 세팅하고 반환한다")
        void getLatestSequence_redisZero_fallbackToDb() {
            // given
            given(chatRoomRedisService.getSequence(10L)).willReturn(0L);
            given(chatRoom.getLastSequence()).willReturn(3L);
            given(chatRoomRepository.findById(10L)).willReturn(Optional.of(chatRoom));

            // when
            Long result = chatRoomService.getLatestSequence(10L);

            // then
            assertThat(result).isEqualTo(3L);
            then(chatRoomRedisService).should().setSequence(10L, 3L);
        }

        @Test
        @DisplayName("Redis 장애 시 DB에서 시퀀스를 폴백으로 반환한다")
        void getLatestSequence_redisDown_fallbackToDb() {
            // given
            given(chatRoomRedisService.getSequence(10L)).willThrow(
                new RuntimeException("Redis down"));
            given(chatRoom.getLastSequence()).willReturn(7L);
            given(chatRoomRepository.findById(10L)).willReturn(Optional.of(chatRoom));

            // when
            Long result = chatRoomService.getLatestSequence(10L);

            // then
            assertThat(result).isEqualTo(7L);
        }
    }

    @Nested
    @DisplayName("findAllRoomsByMemberId() - 내 채팅방 목록 조회")
    class FindAllRoomsByMemberId {

        @Test
        @DisplayName("정상적으로 채팅방 목록과 읽지 않은 메시지 수를 계산해 반환한다")
        void findAllRooms_success_returnsWithUnreadCount() {
            // given
            ChatRoomWithSequenceProjection projection = mock(ChatRoomWithSequenceProjection.class);
            given(projection.getChatRoomId()).willReturn(10L);
            given(projection.getInviteCode()).willReturn("INVITE-CODE");
            given(projection.getName()).willReturn("테스트 방");
            given(projection.getLastReadSequence()).willReturn(3L);

            given(chatRoomRepository.findAllRoomsWithSequenceByParticipantId(1L))
                .willReturn(List.of(projection));
            given(chatRoomRedisService.getSortedRoomIds(List.of(10L))).willReturn(List.of(10L));
            given(chatRoomAlarmRepository.findEnabledMap(1L, List.of(10L)))
                .willReturn(Map.of(10L, true));
            given(chatRoomRedisService.getSequences(List.of(10L))).willReturn(List.of(5L));

            // when
            List<AllRoomsResponse> result = chatRoomService.findAllRoomsByMemberId(1L);

            // then
            assertThat(result).hasSize(1);
            AllRoomsResponse response = result.get(0);
            assertThat(response.getRoomId()).isEqualTo(10L);
            assertThat(response.getUnreadCount()).isEqualTo(2L); // 5 - 3 = 2
            assertThat(response.isAlarmEnabled()).isTrue();
        }

        @Test
        @DisplayName("Redis 정렬 장애 시 원래 순서대로 반환한다")
        void findAllRooms_redisSortFail_usesOriginalOrder() {
            // given
            ChatRoomWithSequenceProjection p1 = mock(ChatRoomWithSequenceProjection.class);
            given(p1.getChatRoomId()).willReturn(10L);
            given(p1.getInviteCode()).willReturn("A");
            given(p1.getName()).willReturn("방A");
            given(p1.getLastReadSequence()).willReturn(0L);

            ChatRoomWithSequenceProjection p2 = mock(ChatRoomWithSequenceProjection.class);
            given(p2.getChatRoomId()).willReturn(20L);
            given(p2.getInviteCode()).willReturn("B");
            given(p2.getName()).willReturn("방B");
            given(p2.getLastReadSequence()).willReturn(0L);

            given(chatRoomRepository.findAllRoomsWithSequenceByParticipantId(1L))
                .willReturn(List.of(p1, p2));
            given(chatRoomRedisService.getSortedRoomIds(any()))
                .willThrow(new RuntimeException("Redis down"));
            given(chatRoomAlarmRepository.findEnabledMap(eq(1L), anyList()))
                .willReturn(Map.of(10L, true, 20L, false));
            given(chatRoomRedisService.getSequences(anyList())).willReturn(List.of(0L, 0L));

            // when
            List<AllRoomsResponse> result = chatRoomService.findAllRoomsByMemberId(1L);

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getRoomId()).isEqualTo(10L);
            assertThat(result.get(1).getRoomId()).isEqualTo(20L);
        }

        @Test
        @DisplayName("Redis sequence 장애 시 DB fallback으로 시퀀스를 가져온다")
        void findAllRooms_redisSequenceFail_fallbackToDb() {
            // given
            ChatRoomWithSequenceProjection projection = mock(ChatRoomWithSequenceProjection.class);
            given(projection.getChatRoomId()).willReturn(10L);
            given(projection.getInviteCode()).willReturn("INVITE-CODE");
            given(projection.getName()).willReturn("테스트 방");
            given(projection.getLastReadSequence()).willReturn(1L);

            given(chatRoomRepository.findAllRoomsWithSequenceByParticipantId(1L))
                .willReturn(List.of(projection));
            given(chatRoomRedisService.getSortedRoomIds(List.of(10L))).willReturn(List.of(10L));
            given(chatRoomAlarmRepository.findEnabledMap(1L, List.of(10L)))
                .willReturn(Map.of(10L, true));

            // Redis 장애
            given(chatRoomRedisService.getSequences(anyList()))
                .willThrow(new RuntimeException("Redis down"));

            // DB 폴백
            given(chatRoom.getLastSequence()).willReturn(4L);
            given(chatRoomRepository.findAllById(List.of(10L))).willReturn(List.of(chatRoom));

            Counter counter = mock(Counter.class);
            given(meterRegistry.counter("redis.fallback", "reason", "sequence")).willReturn(
                counter);

            // when
            List<AllRoomsResponse> result = chatRoomService.findAllRoomsByMemberId(1L);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getUnreadCount()).isEqualTo(3L); // 4 - 1 = 3
            then(counter).should().increment();
        }

        @Test
        @DisplayName("참가한 채팅방이 없으면 빈 리스트를 반환한다")
        void findAllRooms_empty_returnsEmptyList() {
            // given
            given(chatRoomRepository.findAllRoomsWithSequenceByParticipantId(1L))
                .willReturn(Collections.emptyList());

            // when
            List<AllRoomsResponse> result = chatRoomService.findAllRoomsByMemberId(1L);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("syncSequencesToDb() - Redis → DB 시퀀스 동기화")
    class SyncSequencesToDb {

        @Test
        @DisplayName("업데이트된 방이 없으면 조기 반환한다")
        void syncSequencesToDb_empty_earlyReturn() {
            // given
            given(chatRoomRedisService.getAndClearUpdatedRooms()).willReturn(
                Collections.emptySet());

            // when
            chatRoomService.syncSequencesToDb();

            // then
            then(chatRoomRepository).should(never()).findAllById(any());
        }

        @Test
        @DisplayName("Redis 시퀀스가 DB보다 크면 DB를 업데이트한다")
        void syncSequencesToDb_redisHigherThanDb_updatesDb() {
            // given
            given(chatRoomRedisService.getAndClearUpdatedRooms()).willReturn(Set.of("10"));
            given(chatRoom.getLastSequence()).willReturn(3L);
            given(chatRoomRepository.findAllById(List.of(10L))).willReturn(List.of(chatRoom));
            given(chatRoomRedisService.getSequences(List.of(10L))).willReturn(List.of(7L));

            // when
            chatRoomService.syncSequencesToDb();

            // then
            then(chatRoom).should().updateLastSequence(7L);
        }

        @Test
        @DisplayName("Redis 시퀀스가 DB보다 작거나 같으면 업데이트하지 않는다")
        void syncSequencesToDb_redisNotHigher_noUpdate() {
            // given
            given(chatRoomRedisService.getAndClearUpdatedRooms()).willReturn(Set.of("10"));
            given(chatRoom.getLastSequence()).willReturn(10L);
            given(chatRoomRepository.findAllById(List.of(10L))).willReturn(List.of(chatRoom));
            given(chatRoomRedisService.getSequences(List.of(10L))).willReturn(List.of(5L));

            // when
            chatRoomService.syncSequencesToDb();

            // then
            then(chatRoom).should(never()).updateLastSequence(anyLong());
        }
    }
    
    @Nested
    @DisplayName("getRecentRoomInviteCode() - 최근 채팅방 초대 코드 조회")
    class GetRecentRoomInviteCode {

        @Test
        @DisplayName("최근 방이 있으면 초대 코드를 반환한다")
        void getRecentRoomInviteCode_exists_returnsCode() {
            // given
            given(owner.getRecentRoomId()).willReturn(10L);
            given(memberService.getMemberById(1L)).willReturn(owner);
            given(chatRoomRepository.findById(10L)).willReturn(Optional.of(chatRoom));

            // when
            String result = chatRoomService.getRecentRoomInviteCode(1L);

            // then
            assertThat(result).isEqualTo("INVITE-CODE");
        }

        @Test
        @DisplayName("최근 방이 null이면 CHATROOM_NOT_EXIST 예외를 던진다")
        void getRecentRoomInviteCode_null_throwsException() {
            // given
            given(owner.getRecentRoomId()).willReturn(null);
            given(memberService.getMemberById(1L)).willReturn(owner);

            // when & then
            assertThatThrownBy(() -> chatRoomService.getRecentRoomInviteCode(1L))
                .isInstanceOf(ChatRoomException.class);
        }
    }
}