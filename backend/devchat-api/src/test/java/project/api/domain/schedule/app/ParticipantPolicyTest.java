package project.api.domain.schedule.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import project.api.domain.chat.chatroom.dao.ChatParticipantRepository;
import project.api.domain.member.dao.MemberRepository;
import project.api.domain.member.entity.Member;
import project.api.domain.member.friend.dao.FriendsListRepository;
import project.api.global.exception.errorcode.ScheduleErrorCode;
import project.api.global.exception.ex.ScheduleException;

@ExtendWith(MockitoExtension.class)
class ParticipantPolicyTest {

    @Mock
    private MemberRepository memberRepository;
    @Mock
    private FriendsListRepository friendsListRepository;
    @Mock
    private ChatParticipantRepository chatParticipantRepository;
    @InjectMocks
    private ParticipantPolicy policy;

    private Member owner;
    private Member friend;
    private Member roommate;
    private Member stranger;

    private static Member member(Long id) {
        return Member.builder().id(id).username("u" + id).nickname("n" + id).profileImage("p").build();
    }

    @BeforeEach
    void setUp() {
        owner = member(1L);
        friend = member(2L);
        roommate = member(3L);
        stranger = member(4L);
    }

    @Test
    void friend_allowed() {
        given(memberRepository.getReferenceById(1L)).willReturn(owner);
        given(memberRepository.findById(2L)).willReturn(Optional.of(friend));
        given(friendsListRepository.existsByOwnerAndFriend(owner, friend)).willReturn(true);

        List<Member> result = policy.validate(1L, List.of(2L));

        assertThat(result).containsExactly(friend);
    }

    @Test
    void sharedRoom_allowed() {
        given(memberRepository.getReferenceById(1L)).willReturn(owner);
        given(memberRepository.findById(3L)).willReturn(Optional.of(roommate));
        given(friendsListRepository.existsByOwnerAndFriend(owner, roommate)).willReturn(false);
        given(chatParticipantRepository.existsSharedActiveRoom(1L, 3L)).willReturn(true);

        List<Member> result = policy.validate(1L, List.of(3L));

        assertThat(result).containsExactly(roommate);
    }

    @Test
    void stranger_rejected() {
        given(memberRepository.getReferenceById(1L)).willReturn(owner);
        given(memberRepository.findById(4L)).willReturn(Optional.of(stranger));
        given(friendsListRepository.existsByOwnerAndFriend(owner, stranger)).willReturn(false);
        given(chatParticipantRepository.existsSharedActiveRoom(1L, 4L)).willReturn(false);

        assertThatThrownBy(() -> policy.validate(1L, List.of(4L)))
            .isInstanceOf(ScheduleException.class)
            .extracting("errorCode").isEqualTo(ScheduleErrorCode.INVALID_PARTICIPANT);
    }

    @Test
    void unknownMember_rejected() {
        given(memberRepository.getReferenceById(1L)).willReturn(owner);
        given(memberRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> policy.validate(1L, List.of(99L)))
            .isInstanceOf(ScheduleException.class)
            .extracting("errorCode").isEqualTo(ScheduleErrorCode.INVALID_PARTICIPANT);
    }

    @Test
    void selfAndDuplicates_dropped() {
        given(memberRepository.getReferenceById(1L)).willReturn(owner);
        given(memberRepository.findById(2L)).willReturn(Optional.of(friend));
        given(friendsListRepository.existsByOwnerAndFriend(owner, friend)).willReturn(true);

        List<Member> result = policy.validate(1L, Arrays.asList(1L, 2L, 2L, null, 1L));

        assertThat(result).containsExactly(friend);
    }

    @Test
    void nullOrOnlySelf_returnsEmpty() {
        assertThat(policy.validate(1L, null)).isEmpty();
        assertThat(policy.validate(1L, List.of(1L))).isEmpty();
    }
}
