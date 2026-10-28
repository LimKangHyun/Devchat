package project.api.domain.push.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import project.api.domain.chat.chatroom.dao.ChatRoomRepository;
import project.api.domain.chat.chatroom.entity.ChatRoom;
import project.api.domain.member.dao.MemberRepository;
import project.api.domain.member.entity.Member;
import project.api.domain.member.presence.PresenceService;
import project.api.domain.notification.dto.NotificationDto;
import project.api.domain.notification.entity.NotificationType;
import project.api.domain.push.dao.PushSubscriptionRepository;
import project.api.domain.push.dto.PushSubscribeRequest;
import project.api.domain.push.entity.PushSubscription;

class PushNotificationTest {

    private final PushSubscriptionRepository repo = mock(PushSubscriptionRepository.class);
    private final PushSender sender = mock(PushSender.class);
    private final MemberRepository memberRepository = mock(MemberRepository.class);
    private final PushNotificationService service =
        new PushNotificationService(repo, sender, memberRepository, new ObjectMapper(), mock(project.api.domain.notification.app.SeenRoomTracker.class), mock(BadgeCountService.class));

    private static NotificationDto notification(NotificationType type, Long referenceId) {
        return new NotificationDto(1L, false, type, "bob", "alice", "앨리스", null, "내용", referenceId, LocalDateTime.now());
    }

    @Nested
    @DisplayName("PushNotificationService")
    class Service {

        @Test
        @DisplayName("기기마다 보내고, 브라우저가 해지한 구독(410)은 지운다")
        void sendsAndRemovesExpired() throws Exception {
            PushSubscription alive = mock(PushSubscription.class);
            PushSubscription expired = mock(PushSubscription.class);
            given(sender.isEnabled()).willReturn(true);
            given(repo.findByMember_Id(7L)).willReturn(List.of(alive, expired));
            given(sender.send(any(), anyString())).willAnswer(inv -> inv.getArgument(0) == expired ? 410 : 201);

            service.sendToMember(7L, new PushMessage("t", "b", "/", "x"));

            then(repo).should().delete(expired);
            then(repo).should(never()).delete(alive);
        }

        @Test
        @DisplayName("한 기기 발송이 실패해도 다른 기기에는 보낸다")
        void oneFailureDoesNotStopOthers() throws Exception {
            PushSubscription a = mock(PushSubscription.class);
            PushSubscription b = mock(PushSubscription.class);
            given(sender.isEnabled()).willReturn(true);
            given(repo.findByMember_Id(7L)).willReturn(List.of(a, b));
            given(sender.send(any(), anyString())).willAnswer(inv -> {
                if (inv.getArgument(0) == a) throw new RuntimeException("network");
                return 201;
            });

            service.sendToMember(7L, new PushMessage("t", "b", "/", "x"));

            then(sender).should().send(org.mockito.ArgumentMatchers.eq(a), anyString());
            then(sender).should().send(org.mockito.ArgumentMatchers.eq(b), anyString());
        }

        @Test
        @DisplayName("키가 없으면 아무것도 하지 않는다")
        void disabledDoesNothing() {
            given(sender.isEnabled()).willReturn(false);
            service.sendToMember(7L, new PushMessage("t", "b", "/", "x"));
            then(repo).should(never()).findByMember_Id(anyLong());
        }

        @Test
        @DisplayName("같은 기기로 다른 계정이 구독하면 새로 만들지 않고 주인을 바꾼다")
        void resubscribeReassigns() {
            Member bob = mock(Member.class);
            PushSubscription existing = mock(PushSubscription.class);
            given(memberRepository.getReferenceById(2L)).willReturn(bob);
            given(repo.findByEndpointHash(PushSubscription.hash("https://push.example/abc"))).willReturn(Optional.of(existing));

            service.subscribe(2L, new PushSubscribeRequest("https://push.example/abc",
                new PushSubscribeRequest.Keys("pk", "au")));

            then(existing).should().reassign(bob, "pk", "au");
            then(repo).should(never()).save(any());
        }
    }

    @Nested
    @DisplayName("NotificationPushDispatcher")
    class Dispatcher {

        private final PushNotificationService pushService = mock(PushNotificationService.class);
        private final PresenceService presence = mock(PresenceService.class);
        private final ChatRoomRepository roomRepository = mock(ChatRoomRepository.class);
        private final NotificationPushDispatcher dispatcher =
            new NotificationPushDispatcher(pushService, sender, presence, memberRepository, roomRepository);

        private void receiverIs(Long id, boolean online) {
            Member bob = mock(Member.class);
            given(bob.getId()).willReturn(id);
            given(sender.isEnabled()).willReturn(true);
            given(memberRepository.findByUsername("bob")).willReturn(Optional.of(bob));
            given(presence.foregroundAmong(List.of(id))).willReturn(online ? Set.of(id) : Set.of());
        }

        @Test
        @DisplayName("받는 사람이 앱 화면을 보고 있으면 휴대폰 알림은 보내지 않는다")
        void skipWhenOnline() {
            receiverIs(9L, true);
            dispatcher.dispatch("bob", notification(NotificationType.NEW_DM, 3L));
            then(pushService).should(never()).sendToMember(anyLong(), any());
        }

        @Test
        @DisplayName("앱 화면을 보고 있지 않으면 보낸다")
        void sendWhenOffline() {
            receiverIs(9L, false);
            dispatcher.dispatch("bob", notification(NotificationType.NEW_DM, 3L));
            then(pushService).should().sendToMember(org.mockito.ArgumentMatchers.eq(9L), any());
        }

        @Test
        @DisplayName("멘션 알림을 누르면 그 채팅방으로 간다")
        void mentionOpensRoom() {
            ChatRoom room = mock(ChatRoom.class);
            given(room.getInviteCode()).willReturn("abc-123");
            given(roomRepository.findById(20L)).willReturn(Optional.of(room));

            PushMessage msg = dispatcher.toPushMessage(notification(NotificationType.MENTION, 20L));

            assertThat(msg.url()).isEqualTo("/chat/abc-123");
            assertThat(msg.title()).contains("앨리스");
            assertThat(msg.tag()).isEqualTo("MENTION-20");
        }

        @Test
        @DisplayName("스터디 신청 알림은 신청자 목록으로, 그 밖에는 홈으로 간다")
        void otherUrls() {
            assertThat(dispatcher.toPushMessage(notification(NotificationType.STUDY_APPLY, 5L)).url())
                .isEqualTo("/community/5/applicants");
            assertThat(dispatcher.toPushMessage(notification(NotificationType.FRIEND_REQUESTED, 5L)).url())
                .isEqualTo("/");
        }
    }

    @Nested
    @DisplayName("ChatMessagePushListener")
    class ChatPush {

        private final PushNotificationService pushService = mock(PushNotificationService.class);
        private final PresenceService presence = mock(PresenceService.class);
        private final project.api.domain.chat.chatroom.dao.ChatParticipantRepository participants =
            mock(project.api.domain.chat.chatroom.dao.ChatParticipantRepository.class);
        private final ChatRoomRepository roomRepository = mock(ChatRoomRepository.class);
        private final ChatMessagePushListener listener =
            new ChatMessagePushListener(sender, pushService, presence, participants, roomRepository);

        @Test
        @DisplayName("화면을 보고 있는 사람과 멘션된 사람을 빼고, 나머지에게 방 이름·보낸 사람·미리보기로 보낸다")
        void sendsToBackgroundParticipants() {
            ChatRoom room = mock(ChatRoom.class);
            given(room.getId()).willReturn(10L);
            given(room.getName()).willReturn("백엔드");
            given(room.getInviteCode()).willReturn("inv");
            given(sender.isEnabled()).willReturn(true);
            given(participants.findMessagePushTargets(10L, 1L)).willReturn(List.of(2L, 3L, 4L));
            given(presence.foregroundAmong(any())).willReturn(Set.of(3L)); // 3번은 앱을 보고 있음
            given(roomRepository.findById(10L)).willReturn(Optional.of(room));

            listener.onMessage(new project.api.domain.chat.chatmessage.event.ChatMessagePushEvent(
                10L, 1L, "앨리스", "배포했어요", List.of(4L))); // 4번은 멘션됨

            PushMessage expected = new PushMessage("백엔드", "앨리스: 배포했어요", "/chat/inv", "room-10");
            then(pushService).should().sendToMember(2L, expected);
            then(pushService).should(never()).sendToMember(org.mockito.ArgumentMatchers.eq(3L), any());
            then(pushService).should(never()).sendToMember(org.mockito.ArgumentMatchers.eq(4L), any());
        }

        @Test
        @DisplayName("푸시 키가 없으면 대상을 조회하지도 않는다")
        void disabled() {
            given(sender.isEnabled()).willReturn(false);
            listener.onMessage(new project.api.domain.chat.chatmessage.event.ChatMessagePushEvent(
                10L, 1L, "앨리스", "x", List.of()));
            then(participants).should(never()).findMessagePushTargets(anyLong(), anyLong());
        }
    }
}
