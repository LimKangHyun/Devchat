package project.api.domain.chat.chatroom.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import project.api.domain.chat.chatroom.dao.ChatRoomRepository;
import project.api.domain.chat.chatroom.entity.ChatRoom;
import project.api.domain.member.app.MemberService;
import project.api.domain.member.entity.Member;
import project.api.global.exception.errorcode.ChatRoomErrorCode;
import project.api.global.exception.ex.ChatRoomException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChatRoomInviteServiceTest {

    @Mock ChatRoomRepository chatRoomRepository;
    @Mock ChatRoomParticipantService chatRoomParticipantService;
    @Mock MemberService memberService;
    @Mock InviteMailSender inviteMailSender;
    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOps;

    @InjectMocks ChatRoomInviteService service;

    private static final Long ROOM_ID = 1L;
    private static final Long MEMBER_ID = 7L;

    @BeforeEach
    void setUp() {
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(
            ChatRoom.builder().name("스터디방").inviteCode("abc123").build()));
        given(memberService.getMemberById(MEMBER_ID)).willReturn(Member.builder().nickname("림").build());
    }

    @Test
    @DisplayName("주소를 다듬고 중복을 없앤 뒤 초대 메일을 요청한다")
    void normalizesAndSends() {
        given(valueOps.increment(anyString(), eq(2L))).willReturn(2L);

        var res = service.inviteByEmail(ROOM_ID, MEMBER_ID,
            List.of(" A@Example.com", "a@example.com", "b@example.com "));

        assertThat(res.requested()).isEqualTo(2);
        ArgumentCaptor<InviteMailSender.InviteMail> mail = ArgumentCaptor.forClass(InviteMailSender.InviteMail.class);
        verify(inviteMailSender).send(mail.capture());
        assertThat(mail.getValue().emails()).containsExactly("a@example.com", "b@example.com");
        assertThat(mail.getValue().inviteCode()).isEqualTo("abc123");
        assertThat(mail.getValue().roomName()).isEqualTo("스터디방");
        assertThat(mail.getValue().inviterNickname()).isEqualTo("림");
    }

    @Test
    @DisplayName("방에 참여 중이 아니면 보내지 않는다")
    void rejectsNonParticipant() {
        willThrow(new ChatRoomException(ChatRoomErrorCode.NOT_PARTICIPANT))
            .given(chatRoomParticipantService).validateParticipant(MEMBER_ID, ROOM_ID);

        assertThatThrownBy(() -> service.inviteByEmail(ROOM_ID, MEMBER_ID, List.of("a@example.com")))
            .isInstanceOf(ChatRoomException.class);
        verify(inviteMailSender, never()).send(any());
    }

    @Test
    @DisplayName("하루 한도를 넘으면 늘린 수를 되돌리고 거절한다")
    void rejectsOverDailyLimit() {
        given(valueOps.increment(anyString(), anyLong())).willReturn((long) ChatRoomInviteService.DAILY_LIMIT + 1);

        assertThatThrownBy(() -> service.inviteByEmail(ROOM_ID, MEMBER_ID, List.of("a@example.com")))
            .isInstanceOf(ChatRoomException.class)
            .extracting(e -> ((ChatRoomException) e).getErrorCode())
            .isEqualTo(ChatRoomErrorCode.INVITE_LIMIT_EXCEEDED);
        verify(valueOps).decrement(anyString(), eq(1L));
        verify(inviteMailSender, never()).send(any());
    }
}
