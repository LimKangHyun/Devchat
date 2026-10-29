package project.api.domain.schedule.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import project.api.domain.member.dao.MemberRepository;
import project.api.domain.member.entity.Member;
import project.api.domain.schedule.dao.ScheduleRepository;
import project.api.domain.schedule.dto.OccurrenceResponse;
import project.api.domain.schedule.dto.ScheduleRequest;
import project.api.domain.schedule.dto.ScheduleResponse;
import project.api.domain.schedule.entity.RepeatFrequency;
import project.api.domain.schedule.entity.Schedule;
import project.api.domain.schedule.entity.ScheduleDraft;
import project.api.global.exception.errorcode.ScheduleErrorCode;
import project.api.global.exception.ex.ScheduleException;

class ScheduleServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 29, 9, 0);
    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 3, 15, 0);

    private ScheduleRepository scheduleRepository;
    private MemberRepository memberRepository;
    private ParticipantPolicy participantPolicy;
    private ApplicationEventPublisher events;
    private ScheduleService service;

    private Member owner;
    private Member friend;
    private Member other;

    private static Member member(Long id) {
        return Member.builder().id(id).username("u" + id).nickname("n" + id).profileImage("p").build();
    }

    @BeforeEach
    void setUp() {
        scheduleRepository = mock(ScheduleRepository.class);
        memberRepository = mock(MemberRepository.class);
        participantPolicy = mock(ParticipantPolicy.class);
        events = mock(ApplicationEventPublisher.class);
        Clock clock = Clock.fixed(NOW.atZone(KST).toInstant(), KST);
        service = new ScheduleService(scheduleRepository, memberRepository, participantPolicy,
            events, clock);
        owner = member(1L);
        friend = member(2L);
        other = member(3L);
    }

    private static ScheduleRequest timed(String title, LocalDateTime start, LocalDateTime end,
                                         List<Integer> reminders, List<Long> participants) {
        return new ScheduleRequest(title, null, null, start, end, false, RepeatFrequency.NONE,
            null, null, null, reminders, participants);
    }

    private static ScheduleRequest timed(LocalDateTime start) {
        return timed("회의", start, start.plusHours(1), null, List.of());
    }

    // 저장 시 id를 붙여 돌려준다
    private void givenSaveAssignsId(long id) {
        given(scheduleRepository.save(any(Schedule.class))).willAnswer(inv -> {
            Schedule s = inv.getArgument(0);
            ReflectionTestUtils.setField(s, "id", id);
            return s;
        });
    }

    private ScheduleResponse createAsOwner(ScheduleRequest r) {
        given(memberRepository.findById(1L)).willReturn(Optional.of(owner));
        givenSaveAssignsId(10L);
        return service.create(1L, r);
    }

    private Schedule existing(long id, LocalDateTime start, RepeatFrequency freq,
                              List<Member> participants) {
        Schedule s = Schedule.create(owner, new ScheduleDraft("회의", null, null, start,
            start.plusHours(1), false, freq, java.util.Set.of(), null, null, List.of(30)), NOW);
        s.replaceParticipants(participants);
        ReflectionTestUtils.setField(s, "id", id);
        given(scheduleRepository.findById(id)).willReturn(Optional.of(s));
        return s;
    }

    private static void assertCode(Runnable call, ScheduleErrorCode code) {
        assertThatThrownBy(call::run)
            .isInstanceOf(ScheduleException.class)
            .extracting("errorCode").isEqualTo(code);
    }

    @Test
    void create_defaultsReminderByAllDay() {
        ScheduleResponse timedRes = createAsOwner(timed(START));
        assertThat(timedRes.reminderMinutes()).containsExactly(30);

        ScheduleRequest allDay = new ScheduleRequest("워크숍", null, null,
            LocalDateTime.of(2026, 11, 3, 10, 30), LocalDateTime.of(2026, 11, 4, 18, 0), true,
            null, null, null, null, null, null);
        ScheduleResponse allDayRes = service.create(1L, allDay);
        assertThat(allDayRes.reminderMinutes()).containsExactly(900);
        // 하루 종일이면 00:00으로 맞춘다
        assertThat(allDayRes.startAt()).isEqualTo(LocalDateTime.of(2026, 11, 3, 0, 0));
        assertThat(allDayRes.endAt()).isEqualTo(LocalDateTime.of(2026, 11, 4, 0, 0));
    }

    @Test
    void create_emptyRemindersMeansNone_andDedupes() {
        assertThat(createAsOwner(timed("회의", START, START.plusHours(1), List.of(), List.of()))
            .reminderMinutes()).isEmpty();
        assertThat(service.create(1L, timed("회의", START, START.plusHours(1),
            List.of(10, 30, 10), List.of())).reminderMinutes()).containsExactly(10, 30);
    }

    @Test
    void create_rejectsBadReminders() {
        given(memberRepository.findById(1L)).willReturn(Optional.of(owner));
        assertCode(() -> service.create(1L, timed("회의", START, START.plusHours(1),
            Arrays.asList(30, null), List.of())), ScheduleErrorCode.INVALID_SCHEDULE);
        assertCode(() -> service.create(1L, timed("회의", START, START.plusHours(1),
            List.of(1, 2, 3, 4, 5, 6), List.of())), ScheduleErrorCode.INVALID_SCHEDULE);
        assertCode(() -> service.create(1L, timed("회의", START, START.plusHours(1),
            List.of(-1440), List.of())), ScheduleErrorCode.INVALID_SCHEDULE);
        assertCode(() -> service.create(1L, timed("회의", START, START.plusHours(1),
            List.of(10081), List.of())), ScheduleErrorCode.INVALID_SCHEDULE);
    }

    @Test
    void create_rejectsBlankOrLongTitle_trimsTitle() {
        given(memberRepository.findById(1L)).willReturn(Optional.of(owner));
        assertCode(() -> service.create(1L, timed("   ", START, START.plusHours(1), null,
            List.of())), ScheduleErrorCode.INVALID_SCHEDULE);
        assertCode(() -> service.create(1L, timed("가".repeat(101), START, START.plusHours(1),
            null, List.of())), ScheduleErrorCode.INVALID_SCHEDULE);

        givenSaveAssignsId(10L);
        assertThat(service.create(1L, timed("  회의  ", START, START.plusHours(1), null,
            List.of())).title()).isEqualTo("회의");
    }

    @Test
    void create_rejectsEndBeforeStart() {
        given(memberRepository.findById(1L)).willReturn(Optional.of(owner));
        assertCode(() -> service.create(1L, timed("회의", START, START.minusMinutes(1), null,
            List.of())), ScheduleErrorCode.INVALID_SCHEDULE);
        assertCode(() -> service.create(1L, timed("회의", START, START, null, List.of())),
            ScheduleErrorCode.INVALID_SCHEDULE);

        // 하루 종일이면 같은 날도 된다
        givenSaveAssignsId(10L);
        ScheduleRequest sameDay = new ScheduleRequest("워크숍", null, null, START, START, true,
            null, null, null, null, null, null);
        assertThat(service.create(1L, sameDay).endAt()).isEqualTo(START.toLocalDate().atStartOfDay());
    }

    @Test
    void create_rejectsUntilAndCountTogether() {
        given(memberRepository.findById(1L)).willReturn(Optional.of(owner));
        ScheduleRequest both = new ScheduleRequest("회의", null, null, START, START.plusHours(1),
            false, RepeatFrequency.DAILY, null, LocalDate.of(2026, 12, 1), 5, null, null);
        assertCode(() -> service.create(1L, both), ScheduleErrorCode.INVALID_SCHEDULE);

        ScheduleRequest zero = new ScheduleRequest("회의", null, null, START, START.plusHours(1),
            false, RepeatFrequency.DAILY, null, null, 0, null, null);
        assertCode(() -> service.create(1L, zero), ScheduleErrorCode.INVALID_SCHEDULE);
        ScheduleRequest tooMany = new ScheduleRequest("회의", null, null, START, START.plusHours(1),
            false, RepeatFrequency.DAILY, null, null, 366, null, null);
        assertCode(() -> service.create(1L, tooMany), ScheduleErrorCode.INVALID_SCHEDULE);
    }

    @Test
    void create_noneRepeat_clearsRepeatFields() {
        // 반복 없음인데 예전 값이 남아 있으면 회차가 안 나온다 → 비운다
        ScheduleRequest stale = new ScheduleRequest("회의", null, null, START, START.plusHours(1),
            false, RepeatFrequency.NONE, List.of(DayOfWeek.MONDAY), LocalDate.of(2026, 1, 1), null,
            null, null);
        ScheduleResponse res = createAsOwner(stale);

        assertThat(res.repeatFrequency()).isEqualTo(RepeatFrequency.NONE);
        assertThat(res.repeatDays()).isEmpty();
        assertThat(res.repeatUntil()).isNull();
        assertThat(res.repeatCount()).isNull();

        ScheduleRequest staleCount = new ScheduleRequest("회의", null, null, START,
            START.plusHours(1), false, null, null, null, 0, null, null);
        assertThat(service.create(1L, staleCount).repeatCount()).isNull();
    }

    @Test
    void create_weekly_addsStartWeekday() {
        // START는 화요일
        ScheduleRequest weekly = new ScheduleRequest("회의", null, null, START, START.plusHours(1),
            false, RepeatFrequency.WEEKLY, List.of(DayOfWeek.THURSDAY), null, null, null, null);
        ScheduleResponse res = createAsOwner(weekly);

        assertThat(res.repeatDays()).containsExactly(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY);
    }

    @Test
    void create_publishesParticipantsAdded() {
        given(participantPolicy.validate(1L, List.of(2L, 3L))).willReturn(List.of(friend, other));

        ScheduleResponse res = createAsOwner(timed("회의", START, START.plusHours(1), null,
            List.of(2L, 3L)));

        assertThat(res.id()).isEqualTo(10L);
        assertThat(res.mine()).isTrue();
        assertThat(res.participants()).extracting("id").containsExactly(2L, 3L);
        verify(events).publishEvent(new ParticipantsAddedEvent(10L, 1L, List.of(2L, 3L)));
    }

    @Test
    void create_withoutParticipants_publishesNothing() {
        createAsOwner(timed(START));
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void update_byParticipant_forbidden() {
        existing(10L, START, RepeatFrequency.NONE, List.of(friend));

        assertCode(() -> service.update(2L, 10L, timed(START)), ScheduleErrorCode.FORBIDDEN);
    }

    @Test
    void update_byStranger_notFound() {
        existing(10L, START, RepeatFrequency.NONE, List.of(friend));

        assertCode(() -> service.update(3L, 10L, timed(START)), ScheduleErrorCode.NOT_FOUND);
    }

    @Test
    void update_timeChange_publishesChanged_titleOnly_doesNot() {
        existing(10L, START, RepeatFrequency.NONE, List.of(friend));

        service.update(1L, 10L, timed("새 제목", START, START.plusHours(1), List.of(30),
            List.of(2L)));
        verify(events, never()).publishEvent(any(Object.class));

        service.update(1L, 10L, timed("새 제목", START.plusHours(1), START.plusHours(2),
            List.of(30), List.of(2L)));
        verify(events).publishEvent(new ScheduleChangedEvent(10L, 1L));
    }

    @Test
    void update_removedParticipant_publishesCanceledForThem() {
        existing(10L, START, RepeatFrequency.NONE, List.of(friend, other));

        ScheduleResponse res = service.update(1L, 10L, timed("회의", START, START.plusHours(1),
            List.of(30), List.of(2L)));

        assertThat(res.participants()).extracting("id").containsExactly(2L);
        verify(events).publishEvent(new ScheduleCanceledEvent(10L, 1L, "회의", List.of(3L)));
        verify(events, never()).publishEvent(any(ParticipantsAddedEvent.class));
        verify(events, never()).publishEvent(any(ScheduleChangedEvent.class));
    }

    @Test
    void update_keepsExistingParticipantWithoutRecheck() {
        // 친구를 끊었어도 이미 들어간 사람은 제목만 고칠 때 막지 않는다
        existing(10L, START, RepeatFrequency.NONE, List.of(friend));

        ScheduleResponse res = service.update(1L, 10L, timed("새 제목", START, START.plusHours(1),
            List.of(30), List.of(2L)));

        assertThat(res.participants()).extracting("id").containsExactly(2L);
        verify(participantPolicy).validate(1L, List.of());
    }

    @Test
    void update_addedParticipant_publishesAdded() {
        existing(10L, START, RepeatFrequency.NONE, List.of(friend));
        given(participantPolicy.validate(1L, List.of(3L))).willReturn(List.of(other));

        service.update(1L, 10L, timed("회의", START, START.plusHours(1), List.of(30),
            List.of(2L, 3L)));

        verify(events).publishEvent(new ParticipantsAddedEvent(10L, 1L, List.of(3L)));
    }

    @Test
    void delete_byOwner_publishesCanceledWithTitle() {
        Schedule s = existing(10L, START, RepeatFrequency.NONE, List.of(friend));

        service.delete(1L, 10L);

        verify(scheduleRepository).delete(s);
        verify(events).publishEvent(new ScheduleCanceledEvent(10L, 1L, "회의", List.of(2L)));
    }

    @Test
    void delete_byParticipant_forbidden() {
        existing(10L, START, RepeatFrequency.NONE, List.of(friend));

        assertCode(() -> service.delete(2L, 10L), ScheduleErrorCode.FORBIDDEN);
        verify(scheduleRepository, never()).delete(any(Schedule.class));
    }

    @Test
    void get_byStranger_notFound() {
        existing(10L, START, RepeatFrequency.NONE, List.of(friend));
        given(scheduleRepository.findById(99L)).willReturn(Optional.empty());

        assertCode(() -> service.get(3L, 10L), ScheduleErrorCode.NOT_FOUND);
        assertCode(() -> service.get(1L, 99L), ScheduleErrorCode.NOT_FOUND);

        ScheduleResponse asParticipant = service.get(2L, 10L);
        assertThat(asParticipant.mine()).isFalse();
        assertThat(asParticipant.owner().id()).isEqualTo(1L);
    }

    @Test
    void exclude_publishesChanged() {
        Schedule s = existing(10L, START, RepeatFrequency.DAILY, List.of(friend));
        LocalDate date = LocalDate.of(2026, 11, 5);

        service.excludeOccurrence(1L, 10L, date);

        assertThat(s.getExclusions()).extracting("occurrenceDate").containsExactly(date);
        verify(events).publishEvent(new ScheduleChangedEvent(10L, 1L));
    }

    @Test
    void exclude_rejectsNonOccurrenceDate_andParticipant() {
        existing(10L, START, RepeatFrequency.DAILY, List.of(friend));

        assertCode(() -> service.excludeOccurrence(1L, 10L, LocalDate.of(2026, 11, 1)),
            ScheduleErrorCode.INVALID_SCHEDULE);
        assertCode(() -> service.excludeOccurrence(2L, 10L, LocalDate.of(2026, 11, 5)),
            ScheduleErrorCode.FORBIDDEN);
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void leave_removesParticipant_ownerCannotLeave() {
        Schedule s = existing(10L, START, RepeatFrequency.NONE, List.of(friend, other));

        service.leave(2L, 10L);
        assertThat(s.participantIds()).containsExactly(3L);

        assertCode(() -> service.leave(1L, 10L), ScheduleErrorCode.INVALID_SCHEDULE);
        assertCode(() -> service.leave(2L, 10L), ScheduleErrorCode.NOT_FOUND);
    }

    @Test
    void occurrences_rangeOver62Days_rejected() {
        LocalDateTime from = LocalDateTime.of(2026, 11, 1, 0, 0);

        assertCode(() -> service.occurrences(1L, from, from.plusDays(62).plusMinutes(1)),
            ScheduleErrorCode.INVALID_SCHEDULE);
        assertCode(() -> service.occurrences(1L, from, from),
            ScheduleErrorCode.INVALID_SCHEDULE);
        verify(scheduleRepository, never()).findVisibleOverlapping(any(), any(), any());

        given(scheduleRepository.findVisibleOverlapping(1L, from, from.plusDays(62)))
            .willReturn(List.of());
        assertThat(service.occurrences(1L, from, from.plusDays(62))).isEmpty();
    }

    @Test
    void occurrences_expandsRecurring_andKeepsAllDayEndingOnFromDay() {
        LocalDateTime from = LocalDateTime.of(2026, 11, 4, 12, 0);
        LocalDateTime to = LocalDateTime.of(2026, 11, 7, 0, 0);
        Schedule daily = Schedule.create(owner, new ScheduleDraft("스탠드업", null, null,
            LocalDateTime.of(2026, 11, 1, 10, 0), LocalDateTime.of(2026, 11, 1, 10, 15), false,
            RepeatFrequency.DAILY, java.util.Set.of(), null, null, List.of()), NOW);
        ReflectionTestUtils.setField(daily, "id", 20L);
        // 11/3~11/4 하루 종일: endAt은 11/4 00:00
        Schedule allDay = Schedule.create(owner, new ScheduleDraft("워크숍", null, null,
            LocalDateTime.of(2026, 11, 3, 0, 0), LocalDateTime.of(2026, 11, 4, 0, 0), true,
            RepeatFrequency.NONE, java.util.Set.of(), null, null, List.of()), NOW);
        ReflectionTestUtils.setField(allDay, "id", 21L);
        // 리포지토리에는 그날 00:00부터 묻는다
        given(scheduleRepository.findVisibleOverlapping(1L,
            LocalDateTime.of(2026, 11, 4, 0, 0), to)).willReturn(new ArrayList<>(List.of(daily, allDay)));

        List<OccurrenceResponse> result = service.occurrences(1L, from, to);

        // 11/4 10:00 스탠드업은 요청 범위(12:00~) 밖이라 빠진다
        assertThat(result).extracting(OccurrenceResponse::start).containsExactly(
            LocalDateTime.of(2026, 11, 3, 0, 0),
            LocalDateTime.of(2026, 11, 5, 10, 0),
            LocalDateTime.of(2026, 11, 6, 10, 0));
        assertThat(result.get(0).allDay()).isTrue();
        assertThat(result.get(0).recurring()).isFalse();
        assertThat(result.get(1).recurring()).isTrue();
        assertThat(result.get(1).mine()).isTrue();
    }

    @Test
    void upcoming_sortedAndLimited() {
        Schedule daily = Schedule.create(owner, new ScheduleDraft("스탠드업", null, null,
            LocalDateTime.of(2026, 10, 1, 10, 0), LocalDateTime.of(2026, 10, 1, 10, 15), false,
            RepeatFrequency.DAILY, java.util.Set.of(), null, null, List.of()), NOW);
        ReflectionTestUtils.setField(daily, "id", 20L);
        Schedule once = Schedule.create(owner, new ScheduleDraft("회의", null, null,
            LocalDateTime.of(2026, 10, 30, 9, 30), LocalDateTime.of(2026, 10, 30, 11, 0), false,
            RepeatFrequency.NONE, java.util.Set.of(), null, null, List.of()), NOW);
        ReflectionTestUtils.setField(once, "id", 21L);
        Schedule past = Schedule.create(owner, new ScheduleDraft("지난 일", null, null,
            LocalDateTime.of(2026, 10, 28, 9, 0), LocalDateTime.of(2026, 10, 28, 10, 0), false,
            RepeatFrequency.NONE, java.util.Set.of(), null, null, List.of()), NOW);
        ReflectionTestUtils.setField(past, "id", 22L);
        given(scheduleRepository.findAllVisible(1L)).willReturn(List.of(once, daily, past));

        List<OccurrenceResponse> result = service.upcoming(1L, 3);

        // NOW = 10/29 09:00
        assertThat(result).extracting(OccurrenceResponse::start).containsExactly(
            LocalDateTime.of(2026, 10, 29, 10, 0),
            LocalDateTime.of(2026, 10, 30, 9, 30),
            LocalDateTime.of(2026, 10, 30, 10, 0));
        assertThat(result).extracting(OccurrenceResponse::scheduleId).containsExactly(20L, 21L, 20L);

        // 최대 30개
        assertThat(service.upcoming(1L, 100)).hasSize(30);
    }
}
