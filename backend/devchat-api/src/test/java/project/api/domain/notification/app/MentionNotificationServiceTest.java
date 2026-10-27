package project.api.domain.notification.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import project.api.domain.chat.chatmessage.event.ChatMentionEvent;
import project.api.domain.chat.chatroom.dao.ChatParticipantRepository;
import project.api.domain.chat.chatroom.dao.ChatRoomRepository;
import project.api.domain.chat.chatroom.entity.ChatParticipant;
import project.api.domain.chat.chatroom.entity.ChatRoom;
import project.api.domain.member.entity.Member;
import project.api.domain.notification.dao.NotificationRepository;
import project.api.domain.notification.dto.NotificationDto;
import project.api.domain.notification.entity.Notification;
import project.api.domain.notification.entity.NotificationType;

@ExtendWith(MockitoExtension.class)
class MentionNotificationServiceTest {

    @InjectMocks MentionNotificationService service;
    @Mock ChatParticipantRepository chatParticipantRepository;
    @Mock ChatRoomRepository chatRoomRepository;
    @Mock NotificationRepository notificationRepository;
    @Mock ApplicationEventPublisher eventPublisher;

    Member alice = member(1L, "alice", "앨리스");
    Member bob = member(2L, "bob", "밥");
    Member carol = member(3L, "carol", "캐롤");
    Member bot = member(9L, "Devchat-Code-Assist", "봇");

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "githubBotUsername", "GitHubBot");
        ReflectionTestUtils.setField(service, "reviewBotUsername", "Devchat-Code-Assist");
    }

    @Test
    void 멘션된_참여자에게만_알림을_보내고_본인과_봇과_비참여자는_제외한다() {
        List<ChatParticipant> all = List.of(participant(alice), participant(bob), participant(carol), participant(bot));
        given(chatParticipantRepository.findByChatRoomId(10L))
            .willReturn(all);
        ChatRoom room = mock(ChatRoom.class);
        given(room.getName()).willReturn("백엔드");
        given(chatRoomRepository.findById(10L)).willReturn(Optional.of(room));
        given(notificationRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // alice가 bob, 본인(alice), 봇, 방에 없는 사람(77)을 멘션
        int sent = service.notifyMentions(new ChatMentionEvent(10L, 1L, List.of(2L, 1L, 9L, 77L), "리뷰 부탁해요"));

        assertThat(sent).isEqualTo(1);
        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(1)).save(saved.capture());
        assertThat(saved.getValue().getReceiver()).isEqualTo(bob);
        assertThat(saved.getValue().getType()).isEqualTo(NotificationType.MENTION);
        assertThat(saved.getValue().getReferenceId()).isEqualTo(10L);

        ArgumentCaptor<NotificationDto> dto = ArgumentCaptor.forClass(NotificationDto.class);
        verify(eventPublisher).publishEvent(dto.capture());
        assertThat(dto.getValue().receiverUsername()).isEqualTo("bob");
        assertThat(dto.getValue().content()).isEqualTo("앨리스님이 백엔드에서 언급했습니다: 리뷰 부탁해요");
    }

    @Test
    void 보낸_사람이_방_참여자가_아니면_보내지_않는다() {
        List<ChatParticipant> onlyBob = List.of(participant(bob));
        given(chatParticipantRepository.findByChatRoomId(10L)).willReturn(onlyBob);

        int sent = service.notifyMentions(new ChatMentionEvent(10L, 1L, List.of(2L), "hi"));

        assertThat(sent).isZero();
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void 본인만_멘션하면_조회도_하지_않는다() {
        assertThat(service.notifyMentions(new ChatMentionEvent(10L, 1L, List.of(1L), "hi"))).isZero();
        verify(chatParticipantRepository, never()).findByChatRoomId(any());
    }

    private static Member member(Long id, String username, String nickname) {
        return Member.builder().id(id).username(username).nickname(nickname).build();
    }

    private static ChatParticipant participant(Member member) {
        ChatParticipant p = mock(ChatParticipant.class, org.mockito.Mockito.withSettings().strictness(org.mockito.quality.Strictness.LENIENT));
        given(p.getParticipant()).willReturn(member);
        return p;
    }
}
