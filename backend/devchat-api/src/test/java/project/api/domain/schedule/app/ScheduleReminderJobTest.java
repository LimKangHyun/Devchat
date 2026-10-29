package project.api.domain.schedule.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import project.api.domain.member.entity.Member;
import project.api.domain.schedule.dao.ScheduleReminderDispatchRepository;
import project.api.domain.schedule.dao.ScheduleRepository;
import project.api.domain.schedule.entity.RepeatFrequency;
import project.api.domain.schedule.entity.Schedule;
import project.api.domain.schedule.entity.ScheduleDraft;
import project.api.domain.schedule.entity.ScheduleReminderDispatch;

@ExtendWith(MockitoExtension.class)
class ScheduleReminderJobTest {

    private static final LocalDateTime CREATED = LocalDateTime.of(2026, 10, 29, 12, 0);

    @Mock ScheduleRepository scheduleRepository;
    @Mock ScheduleReminderDispatchRepository dispatchRepository;
    @Mock ScheduleNotifier notifier;

    private final Member owner = Member.builder().id(1L).nickname("a").build();
    private ScheduleReminderJob job;

    @BeforeEach
    void setUp() {
        PlatformTransactionManager tx = mock(PlatformTransactionManager.class);
        job = new ScheduleReminderJob(scheduleRepository, dispatchRepository, notifier, tx);
    }

    private Schedule schedule(long id, LocalDateTime start, LocalDateTime end, boolean allDay,
                              RepeatFrequency f, Set<DayOfWeek> days, List<Integer> reminders) {
        ScheduleDraft d = new ScheduleDraft("t", null, null, start, end, allDay,
            f, days, null, null, reminders);
        Schedule s = Schedule.create(owner, d, CREATED);
        ReflectionTestUtils.setField(s, "id", id);
        return s;
    }

    private Schedule timed15(List<Integer> reminders) {
        return schedule(7L, LocalDateTime.of(2026, 11, 3, 15, 0), LocalDateTime.of(2026, 11, 3, 16, 0),
            false, RepeatFrequency.NONE, null, reminders);
    }

    private void candidates(Schedule s) {
        given(scheduleRepository.findReminderCandidates(any(), any())).willReturn(List.of(s));
    }

    // 발송할 때 다시 읽어 오는 일정
    private void reload(Schedule s) {
        given(scheduleRepository.findById(s.getId())).willReturn(Optional.of(s));
    }

    @Test
    void timed30_firesAtStartMinus30() {
        Schedule s = timed15(List.of(30));
        candidates(s);
        reload(s);

        job.run(LocalDateTime.of(2026, 11, 3, 14, 30));

        ArgumentCaptor<ScheduleReminderDispatch> saved = ArgumentCaptor.forClass(ScheduleReminderDispatch.class);
        verify(dispatchRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getScheduleId()).isEqualTo(7L);
        assertThat(saved.getValue().getOccurrenceStart()).isEqualTo(LocalDateTime.of(2026, 11, 3, 15, 0));
        assertThat(saved.getValue().getMinutesBefore()).isEqualTo(30);
        verify(notifier, times(1)).notifyReminder(eq(s),
            eq(new Occurrence(LocalDateTime.of(2026, 11, 3, 15, 0), LocalDateTime.of(2026, 11, 3, 16, 0))));
    }

    @Test
    void notYet_doesNotFire() {
        candidates(timed15(List.of(30)));

        job.run(LocalDateTime.of(2026, 11, 3, 14, 29));

        verify(dispatchRepository, never()).saveAndFlush(any());
        verify(notifier, never()).notifyReminder(any(), any());
    }

    @Test
    void catchUp_withinTenMinutes_sends() {
        Schedule s = timed15(List.of(30));
        candidates(s);
        reload(s);

        job.run(LocalDateTime.of(2026, 11, 3, 14, 35));

        verify(notifier, times(1)).notifyReminder(eq(s), any());
    }

    @Test
    void staleOverTenMinutes_skipped() {
        candidates(timed15(List.of(30)));

        job.run(LocalDateTime.of(2026, 11, 3, 14, 45));

        verify(dispatchRepository, never()).saveAndFlush(any());
        verify(notifier, never()).notifyReminder(any(), any());
    }

    @Test
    void duplicateInsert_skipsNotify() {
        candidates(timed15(List.of(30)));
        willThrow(new DataIntegrityViolationException("uk_schedule_dispatch"))
            .given(dispatchRepository).saveAndFlush(any());

        job.run(LocalDateTime.of(2026, 11, 3, 14, 30));

        verify(notifier, never()).notifyReminder(any(), any());
    }

    @Test
    void alreadySent_skipsInsertAndNotify() {
        candidates(timed15(List.of(30)));
        given(dispatchRepository.existsByScheduleIdAndOccurrenceStartAndMinutesBefore(
            7L, LocalDateTime.of(2026, 11, 3, 15, 0), 30)).willReturn(true);

        job.run(LocalDateTime.of(2026, 11, 3, 14, 35));

        verify(dispatchRepository, never()).saveAndFlush(any());
        verify(notifier, never()).notifyReminder(any(), any());
    }

    @Test
    void allDaySameDayReminder_firesAfterStart() {
        Schedule s = schedule(8L, LocalDateTime.of(2026, 11, 3, 0, 0), LocalDateTime.of(2026, 11, 3, 0, 0),
            true, RepeatFrequency.NONE, null, List.of(-540));
        candidates(s);
        reload(s);

        job.run(LocalDateTime.of(2026, 11, 3, 9, 0));

        verify(notifier, times(1)).notifyReminder(eq(s), any());
    }

    @Test
    void weeklyOccurrence_eachWeek() {
        Schedule s = schedule(9L, LocalDateTime.of(2026, 11, 2, 10, 0), LocalDateTime.of(2026, 11, 2, 11, 0),
            false, RepeatFrequency.WEEKLY, Set.of(DayOfWeek.MONDAY), List.of(30));
        candidates(s);
        reload(s);

        job.run(LocalDateTime.of(2026, 11, 9, 9, 30));

        verify(notifier, times(1)).notifyReminder(eq(s),
            eq(new Occurrence(LocalDateTime.of(2026, 11, 9, 10, 0), LocalDateTime.of(2026, 11, 9, 11, 0))));
    }

    @Test
    void notifyFailure_continuesWithNext() {
        Schedule first = timed15(List.of(30));
        Schedule second = schedule(10L, LocalDateTime.of(2026, 11, 3, 15, 0), LocalDateTime.of(2026, 11, 3, 16, 0),
            false, RepeatFrequency.NONE, null, List.of(30));
        given(scheduleRepository.findReminderCandidates(any(), any())).willReturn(List.of(first, second));
        reload(first);
        reload(second);
        willThrow(new RuntimeException("push down")).given(notifier).notifyReminder(eq(first), any());

        job.run(LocalDateTime.of(2026, 11, 3, 14, 30));

        verify(notifier).notifyReminder(eq(second), any());
    }

    @Test
    void cleanup_deletesOlderThan30Days() {
        given(dispatchRepository.deleteSentBefore(any())).willReturn(3);

        job.deleteOldDispatches(LocalDateTime.of(2026, 11, 30, 4, 20));

        verify(dispatchRepository).deleteSentBefore(LocalDateTime.of(2026, 10, 31, 4, 20));
    }
}
