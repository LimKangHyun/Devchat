package project.api.domain.schedule.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import project.api.domain.member.entity.Member;
import project.api.domain.schedule.entity.RepeatFrequency;
import project.api.domain.schedule.entity.Schedule;
import project.api.domain.schedule.entity.ScheduleDraft;

class RecurrenceExpanderTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 29, 12, 0);
    private final Member owner = Member.builder().id(1L).nickname("a").build();

    private Schedule schedule(LocalDateTime start, LocalDateTime end, boolean allDay,
                              RepeatFrequency f, Set<DayOfWeek> days,
                              LocalDate until, Integer count) {
        ScheduleDraft d = new ScheduleDraft("t", null, null, start, end, allDay,
            f, days, until, count, List.of());
        return Schedule.create(owner, d, NOW);
    }

    private static LocalDateTime at(int y, int m, int d, int h) {
        return LocalDateTime.of(y, m, d, h, 0);
    }

    private static List<LocalDate> dates(List<Occurrence> list) {
        return list.stream().map(o -> o.start().toLocalDate()).toList();
    }

    @Test
    void none_returnsSingleWhenOverlapping() {
        Schedule s = schedule(at(2026, 11, 2, 10), at(2026, 11, 2, 11), false,
            RepeatFrequency.NONE, null, null, null);

        assertThat(RecurrenceExpander.between(s, at(2026, 11, 1, 0), at(2026, 11, 3, 0)))
            .containsExactly(new Occurrence(at(2026, 11, 2, 10), at(2026, 11, 2, 11)));
        assertThat(RecurrenceExpander.between(s, at(2026, 11, 3, 0), at(2026, 11, 4, 0)))
            .isEmpty();
    }

    @Test
    void daily_withCount3() {
        Schedule s = schedule(at(2026, 11, 2, 10), at(2026, 11, 2, 11), false,
            RepeatFrequency.DAILY, null, null, 3);

        List<Occurrence> result = RecurrenceExpander.between(s, at(2026, 11, 1, 0), at(2026, 12, 1, 0));

        assertThat(result).containsExactly(
            new Occurrence(at(2026, 11, 2, 10), at(2026, 11, 2, 11)),
            new Occurrence(at(2026, 11, 3, 10), at(2026, 11, 3, 11)),
            new Occurrence(at(2026, 11, 4, 10), at(2026, 11, 4, 11)));
    }

    @Test
    void weekly_monWedFri_fromWednesdayStart() {
        Schedule s = schedule(at(2026, 11, 4, 9), at(2026, 11, 4, 10), false,
            RepeatFrequency.WEEKLY, Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
            LocalDate.of(2026, 11, 13), null);

        List<Occurrence> result = RecurrenceExpander.between(s, at(2026, 10, 1, 0), at(2026, 12, 31, 0));

        assertThat(dates(result)).containsExactly(
            LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 6), LocalDate.of(2026, 11, 9),
            LocalDate.of(2026, 11, 11), LocalDate.of(2026, 11, 13));
    }

    @Test
    void weekly_emptyDays_usesStartDay() {
        Schedule s = schedule(at(2026, 11, 4, 9), at(2026, 11, 4, 10), false,
            RepeatFrequency.WEEKLY, Set.of(), null, 3);

        assertThat(dates(RecurrenceExpander.between(s, at(2026, 11, 1, 0), at(2027, 1, 1, 0))))
            .containsExactly(LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 11),
                LocalDate.of(2026, 11, 18));
    }

    @Test
    void monthly31_skipsShortMonths() {
        Schedule s = schedule(at(2026, 1, 31, 10), at(2026, 1, 31, 11), false,
            RepeatFrequency.MONTHLY, null, null, null);

        List<Occurrence> result = RecurrenceExpander.between(s, at(2026, 1, 1, 0), at(2026, 7, 1, 0));

        assertThat(dates(result)).containsExactly(
            LocalDate.of(2026, 1, 31), LocalDate.of(2026, 3, 31), LocalDate.of(2026, 5, 31));
    }

    @Test
    void monthly31_countSkipsDoNotCount() {
        Schedule s = schedule(at(2026, 1, 31, 10), at(2026, 1, 31, 11), false,
            RepeatFrequency.MONTHLY, null, null, 3);

        assertThat(dates(RecurrenceExpander.between(s, at(2026, 1, 1, 0), at(2027, 1, 1, 0))))
            .containsExactly(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 3, 31),
                LocalDate.of(2026, 5, 31));
    }

    @Test
    void yearlyLeapDay_onlyLeapYears() {
        Schedule s = schedule(at(2024, 2, 29, 0), at(2024, 2, 29, 0), true,
            RepeatFrequency.YEARLY, null, null, null);

        List<Occurrence> result = RecurrenceExpander.between(s, at(2024, 1, 1, 0), at(2030, 1, 1, 0));

        assertThat(dates(result)).containsExactly(
            LocalDate.of(2024, 2, 29), LocalDate.of(2028, 2, 29));
    }

    @Test
    void exclusion_removesThatDate() {
        Schedule s = schedule(at(2026, 11, 2, 10), at(2026, 11, 2, 11), false,
            RepeatFrequency.DAILY, null, null, 5);
        s.exclude(LocalDate.of(2026, 11, 4));

        List<Occurrence> result = RecurrenceExpander.between(s, at(2026, 11, 1, 0), at(2026, 12, 1, 0));

        assertThat(dates(result)).containsExactly(
            LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 3),
            LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 6));
    }

    @Test
    void countIncludesExcluded() {
        Schedule s = schedule(at(2026, 11, 2, 10), at(2026, 11, 2, 11), false,
            RepeatFrequency.DAILY, null, null, 3);
        s.exclude(LocalDate.of(2026, 11, 3));

        List<Occurrence> result = RecurrenceExpander.between(s, at(2026, 11, 1, 0), at(2026, 12, 1, 0));

        assertThat(dates(result)).containsExactly(
            LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 4));
    }

    @Test
    void until_isInclusive() {
        Schedule s = schedule(at(2026, 11, 2, 23), at(2026, 11, 2, 23), false,
            RepeatFrequency.DAILY, null, LocalDate.of(2026, 11, 4), null);

        assertThat(dates(RecurrenceExpander.between(s, at(2026, 11, 1, 0), at(2026, 12, 1, 0))))
            .containsExactly(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 3),
                LocalDate.of(2026, 11, 4));
    }

    @Test
    void rangeFilter_overlapOnly() {
        // 11/1 ~ 11/3 하루 종일 (끝 날짜 포함)
        Schedule s = schedule(at(2026, 11, 1, 0), at(2026, 11, 3, 0), true,
            RepeatFrequency.NONE, null, null, null);

        // 범위 11/2 ~ 11/5: 둘째 날에 걸쳐 있다
        assertThat(RecurrenceExpander.between(s, at(2026, 11, 2, 0), at(2026, 11, 5, 0)))
            .containsExactly(new Occurrence(at(2026, 11, 1, 0), at(2026, 11, 3, 0)));
        // 범위가 일정 시작일에 끝나면(배타) 포함하지 않는다
        assertThat(RecurrenceExpander.between(s, at(2026, 10, 28, 0), at(2026, 11, 1, 0)))
            .isEmpty();
        // 끝난 다음 날부터는 포함하지 않는다
        assertThat(RecurrenceExpander.between(s, at(2026, 11, 4, 0), at(2026, 11, 6, 0)))
            .isEmpty();
    }

    @Test
    void rangeFilter_keepsLength() {
        Schedule s = schedule(at(2026, 11, 2, 22), at(2026, 11, 3, 1), false,
            RepeatFrequency.WEEKLY, null, null, null);

        assertThat(RecurrenceExpander.between(s, at(2026, 11, 10, 0), at(2026, 11, 10, 2)))
            .containsExactly(new Occurrence(at(2026, 11, 9, 22), at(2026, 11, 10, 1)));
    }

    @Test
    void next_returnsFirstAtOrAfter() {
        Schedule s = schedule(at(2026, 11, 2, 10), at(2026, 11, 2, 11), false,
            RepeatFrequency.DAILY, null, null, 5);
        s.exclude(LocalDate.of(2026, 11, 4));

        assertThat(RecurrenceExpander.next(s, at(2026, 11, 3, 10)))
            .contains(new Occurrence(at(2026, 11, 3, 10), at(2026, 11, 3, 11)));
        // 제외된 날은 건너뛴다
        assertThat(RecurrenceExpander.next(s, at(2026, 11, 3, 10).plusMinutes(1)))
            .contains(new Occurrence(at(2026, 11, 5, 10), at(2026, 11, 5, 11)));
        // 끝난 뒤에는 없다
        assertThat(RecurrenceExpander.next(s, at(2026, 11, 7, 0))).isEqualTo(Optional.empty());
    }

    @Test
    void endless_stopsAtSafetyCap() {
        Schedule s = schedule(at(2026, 1, 1, 10), at(2026, 1, 1, 11), false,
            RepeatFrequency.DAILY, null, null, null);

        List<Occurrence> all = RecurrenceExpander.between(s, at(2000, 1, 1, 0), at(2100, 1, 1, 0));

        assertThat(all).hasSize(2000);
        assertThat(RecurrenceExpander.next(s, at(2099, 1, 1, 0))).isEmpty();
    }
}
