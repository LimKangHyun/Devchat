package project.api.domain.schedule.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import project.api.domain.member.dao.MemberRepository;
import project.api.domain.member.entity.Member;
import project.api.domain.notification.UserNotificationSender;
import project.api.domain.notification.app.NotificationService;
import project.api.domain.notification.dto.NotificationDto;
import project.api.domain.notification.entity.Notification;
import project.api.domain.notification.entity.NotificationType;
import project.api.domain.schedule.dao.ScheduleRepository;
import project.api.domain.schedule.entity.RepeatFrequency;
import project.api.domain.schedule.entity.Schedule;
import project.api.domain.schedule.entity.ScheduleDraft;

class ScheduleNotifierTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 29, 12, 0);

    private final NotificationService notificationService = mock(NotificationService.class);
    private final UserNotificationSender sender = mock(UserNotificationSender.class);
    private final MemberRepository memberRepository = mock(MemberRepository.class);
    private final ScheduleRepository scheduleRepository = mock(ScheduleRepository.class);
    private final ScheduleNotifier notifier =
        new ScheduleNotifier(notificationService, sender, memberRepository, scheduleRepository,
            mock(PlatformTransactionManager.class));

    private final Member minsu = member(1L, "minsu", "민수");
    private final Member jiwon = member(2L, "jiwon", "지원");
    private final Member taeho = member(3L, "taeho", "태호");

    private static Member member(long id, String username, String nickname) {
        return Member.builder().id(id).username(username).nickname(nickname).build();
    }

    @BeforeEach
    void saveEchoes() {
        given(notificationService.saveNotification(any(Notification.class)))
            .willAnswer(inv -> inv.getArgument(0));
    }

    private Schedule schedule(String title, boolean allDay, LocalDateTime start, Member... guests) {
        ScheduleDraft d = new ScheduleDraft(title, null, null, start, start.plusHours(1), allDay,
            RepeatFrequency.NONE, null, null, null, List.of());
        Schedule s = Schedule.create(minsu, d, NOW);
        ReflectionTestUtils.setField(s, "id", 7L);
        s.replaceParticipants(List.of(guests));
        return s;
    }

    private List<NotificationDto> sentTo(String username, int times) {
        ArgumentCaptor<Object> cap = ArgumentCaptor.forClass(Object.class);
        verify(sender, times(times)).send(eq(username), cap.capture());
        return cap.getAllValues().stream().map(NotificationDto.class::cast).toList();
    }

    @Test
    void added_sendsToNewParticipantsWithText() {
        Schedule s = schedule("스프린트 회의", false, NOW.plusDays(1), jiwon);
        given(scheduleRepository.findById(7L)).willReturn(Optional.of(s));
        given(memberRepository.findById(1L)).willReturn(Optional.of(minsu));
        given(memberRepository.findAllById(List.of(2L))).willReturn(List.of(jiwon));

        notifier.onParticipantsAdded(new ParticipantsAddedEvent(7L, 1L, List.of(2L)));

        NotificationDto dto = sentTo("jiwon", 1).get(0);
        assertThat(dto.type()).isEqualTo(NotificationType.SCHEDULE_ADDED);
        assertThat(dto.content()).isEqualTo("민수님이 '스프린트 회의' 일정에 추가했어요");
        assertThat(dto.referenceId()).isEqualTo(7L);
        assertThat(dto.senderUsername()).isEqualTo("minsu");
    }

    @Test
    void added_skipsActor() {
        Schedule s = schedule("회의", false, NOW.plusDays(1), jiwon);
        given(scheduleRepository.findById(7L)).willReturn(Optional.of(s));
        given(memberRepository.findById(1L)).willReturn(Optional.of(minsu));
        given(memberRepository.findAllById(List.of(1L, 2L))).willReturn(List.of(minsu, jiwon));

        notifier.onParticipantsAdded(new ParticipantsAddedEvent(7L, 1L, List.of(1L, 2L)));

        verify(sender, never()).send(eq("minsu"), any());
        sentTo("jiwon", 1);
    }

    @Test
    void failureOfOneRecipient_doesNotStopOthers() {
        given(memberRepository.findById(1L)).willReturn(Optional.of(minsu));
        given(memberRepository.findAllById(List.of(2L, 3L))).willReturn(List.of(jiwon, taeho));
        given(notificationService.saveNotification(any(Notification.class)))
            .willThrow(new RuntimeException("db"))
            .willAnswer(inv -> inv.getArgument(0));

        notifier.onCanceled(new ScheduleCanceledEvent(7L, 1L, "회의", List.of(2L, 3L)));

        verify(sender, never()).send(eq("jiwon"), any());
        sentTo("taeho", 1);
    }

    @Test
    void changed_skipsActor() {
        Schedule s = schedule("회의", false, NOW.plusDays(1), jiwon, taeho);
        given(scheduleRepository.findById(7L)).willReturn(Optional.of(s));
        given(memberRepository.findById(1L)).willReturn(Optional.of(minsu));
        given(memberRepository.findAllById(List.of(2L, 3L))).willReturn(List.of(jiwon, taeho));

        notifier.onChanged(new ScheduleChangedEvent(7L, 1L, List.of(2L, 3L)));

        assertThat(sentTo("jiwon", 1).get(0).content()).isEqualTo("'회의' 일정이 바뀌었어요");
        assertThat(sentTo("taeho", 1).get(0).type()).isEqualTo(NotificationType.SCHEDULE_CHANGED);
        verify(sender, never()).send(eq("minsu"), any());
    }

    @Test
    void changed_newlyAddedParticipantNotNotified() {
        // 일정에는 지원·태호가 있지만 이번에 새로 들어온 태호는 이벤트 대상이 아니다
        Schedule s = schedule("회의", false, NOW.plusDays(1), jiwon, taeho);
        given(scheduleRepository.findById(7L)).willReturn(Optional.of(s));
        given(memberRepository.findById(1L)).willReturn(Optional.of(minsu));
        given(memberRepository.findAllById(List.of(2L))).willReturn(List.of(jiwon));

        notifier.onChanged(new ScheduleChangedEvent(7L, 1L, List.of(2L)));

        sentTo("jiwon", 1);
        verify(sender, never()).send(eq("taeho"), any());
    }

    @Test
    void changed_actorIsParticipantNotNotified() {
        // 만든 사람이 아닌 사람이 actor여도 본인은 뺀다
        Schedule s = schedule("회의", false, NOW.plusDays(1), jiwon);
        given(scheduleRepository.findById(7L)).willReturn(Optional.of(s));
        given(memberRepository.findById(2L)).willReturn(Optional.of(jiwon));
        given(memberRepository.findAllById(List.of(2L))).willReturn(List.of(jiwon));

        notifier.onChanged(new ScheduleChangedEvent(7L, 2L, List.of(2L)));

        verify(sender, never()).send(eq("jiwon"), any());
    }

    @Test
    void canceled_usesTitleFromEvent() {
        given(memberRepository.findById(1L)).willReturn(Optional.of(minsu));
        given(memberRepository.findAllById(List.of(2L, 3L))).willReturn(List.of(jiwon, taeho));

        notifier.onCanceled(new ScheduleCanceledEvent(7L, 1L, "삭제된 회의", List.of(2L, 3L)));

        assertThat(sentTo("jiwon", 1).get(0).content()).isEqualTo("'삭제된 회의' 일정이 취소됐어요");
        assertThat(sentTo("taeho", 1).get(0).type()).isEqualTo(NotificationType.SCHEDULE_CANCELED);
    }

    @Test
    void canceled_emptyMembersDoesNothing() {
        notifier.onCanceled(new ScheduleCanceledEvent(7L, 1L, "회의", List.of()));

        verify(notificationService, never()).saveNotification(any());
        verify(sender, never()).send(any(), any());
    }

    @Test
    void reminder_timedText() {
        Schedule s = schedule("회의", false, LocalDateTime.of(2026, 11, 3, 15, 0), jiwon);
        Occurrence o = new Occurrence(LocalDateTime.of(2026, 11, 3, 15, 0),
            LocalDateTime.of(2026, 11, 3, 16, 0));

        notifier.notifyReminder(s, o);

        NotificationDto toOwner = sentTo("minsu", 1).get(0);
        assertThat(toOwner.content()).isEqualTo("'회의' 11월 3일 (화) 오후 3:00");
        assertThat(toOwner.type()).isEqualTo(NotificationType.SCHEDULE_REMINDER);
        assertThat(toOwner.senderUsername()).isEqualTo("minsu");
        assertThat(sentTo("jiwon", 1).get(0).content()).isEqualTo("'회의' 11월 3일 (화) 오후 3:00");
    }

    @Test
    void reminder_allDayText() {
        Schedule s = schedule("워크숍", true, LocalDateTime.of(2026, 11, 3, 0, 0));
        Occurrence o = new Occurrence(LocalDateTime.of(2026, 11, 3, 0, 0),
            LocalDateTime.of(2026, 11, 3, 0, 0));

        notifier.notifyReminder(s, o);

        assertThat(sentTo("minsu", 1).get(0).content()).isEqualTo("'워크숍' 11월 3일 (화) 하루 종일");
    }
}
