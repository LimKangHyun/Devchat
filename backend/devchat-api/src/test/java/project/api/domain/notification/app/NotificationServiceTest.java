package project.api.domain.notification.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import project.api.auth.dto.MemberDetails;
import project.api.domain.member.app.MemberService;
import project.api.domain.member.entity.Member;
import project.api.domain.notification.dao.NotificationRepository;
import project.api.domain.notification.entity.Notification;
import project.api.domain.notification.entity.NotificationType;
import project.api.global.exception.ex.NotificationException;

class NotificationServiceTest {

    private final NotificationRepository repo = mock(NotificationRepository.class);
    private final MemberService memberService = mock(MemberService.class);
    private final SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
    private final NotificationService service = new NotificationService(repo, memberService, messaging);
    private final Authentication auth = mock(Authentication.class);

    private void loggedInAs(Long id, String username) {
        MemberDetails me = mock(MemberDetails.class);
        given(me.getId()).willReturn(id);
        given(me.getUsername()).willReturn(username);
        given(memberService.checkAuthentication(auth)).willReturn(me);
    }

    @Test
    @DisplayName("방을 열면 그 방의 멘션 알림을 읽음 처리하고 내 다른 창에 알린다")
    void readMentionsInRoom() {
        loggedInAs(1L, "alice");
        given(repo.markReadByRoom(1L, 20L, NotificationType.MENTION)).willReturn(2);

        assertThat(service.readMentionsInRoom(auth, 20L)).isEqualTo(2);
        then(messaging).should().convertAndSendToUser(eq("alice"), eq(NotificationService.SYNC_DESTINATION), any(Object.class));
    }

    @Test
    @DisplayName("읽을 멘션이 없으면 다른 창에 알리지 않는다")
    void nothingToRead() {
        loggedInAs(1L, "alice");
        given(repo.markReadByRoom(1L, 20L, NotificationType.MENTION)).willReturn(0);

        assertThat(service.readMentionsInRoom(auth, 20L)).isZero();
        then(messaging).should(never()).convertAndSendToUser(anyString(), anyString(), any(Object.class));
    }

    @Test
    @DisplayName("남의 알림은 읽음 처리할 수 없다")
    void cannotReadOthersNotification() {
        loggedInAs(1L, "alice");
        Member bob = mock(Member.class);
        given(bob.getId()).willReturn(2L);
        Notification bobs = mock(Notification.class);
        given(bobs.getReceiver()).willReturn(bob);
        given(repo.findById(99L)).willReturn(Optional.of(bobs));

        assertThatThrownBy(() -> service.readNotification(99L, auth)).isInstanceOf(NotificationException.class);
        then(bobs).should(never()).markAsRead();
    }

    @Test
    @DisplayName("30일 지난 알림을 지우되, 처리 안 한 친구 요청은 남기도록 요청한다")
    void cleanup() {
        new NotificationCleanupJob(repo).deleteOldNotifications();
        then(repo).should().deleteOlderThan(
            org.mockito.ArgumentMatchers.argThat((LocalDateTime t) ->
                t.isBefore(LocalDateTime.now().minusDays(29)) && t.isAfter(LocalDateTime.now().minusDays(31))),
            eq(NotificationType.FRIEND_REQUESTED));
    }
}
