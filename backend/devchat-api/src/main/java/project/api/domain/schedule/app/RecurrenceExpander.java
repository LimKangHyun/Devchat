package project.api.domain.schedule.app;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import project.api.domain.schedule.entity.RepeatFrequency;
import project.api.domain.schedule.entity.Schedule;
import project.api.domain.schedule.entity.ScheduleExclusion;

// 반복 규칙으로 회차를 펼친다. DB나 시계에 의존하지 않는 순수 계산
public final class RecurrenceExpander {

    // 무한 반복을 막는 상한 (뺀 날짜도 센다)
    static final int MAX_OCCURRENCES = 2000;

    private RecurrenceExpander() {
    }

    /** [start, end]가 [from, to)와 겹치는 회차를 시작 순으로 */
    public static List<Occurrence> between(Schedule s, LocalDateTime from, LocalDateTime to) {
        List<Occurrence> result = new ArrayList<>();
        expand(Rule.of(s), o -> {
            if (!o.start().isBefore(to)) {
                return false;
            }
            if (!o.end().isBefore(from)) {
                result.add(o);
            }
            return true;
        });
        return result;
    }

    /** start >= after인 첫 회차 */
    public static Optional<Occurrence> next(Schedule s, LocalDateTime after) {
        Occurrence[] found = new Occurrence[1];
        expand(Rule.of(s), o -> {
            if (!o.start().isBefore(after)) {
                found[0] = o;
                return false;
            }
            return true;
        });
        return Optional.ofNullable(found[0]);
    }

    /** 회차 계산에 필요한 값만 모은 것. length = end - start */
    record Rule(LocalDateTime startAt, Duration length, RepeatFrequency frequency,
                Set<DayOfWeek> days, LocalDate until, Integer count, Set<LocalDate> exclusions) {

        static Rule of(Schedule s) {
            Set<LocalDate> excluded = s.getExclusions().stream()
                .map(ScheduleExclusion::getOccurrenceDate)
                .collect(Collectors.toSet());
            return new Rule(s.getStartAt(), Duration.between(s.getStartAt(), s.getEndAt()),
                s.getRepeatFrequency(), s.getRepeatDays(), s.getRepeatUntil(),
                s.getRepeatCount(), excluded);
        }
    }

    /**
     * 회차를 시작 순으로 하나씩 visitor에 넘긴다. visitor가 false를 돌려주면 멈춘다.
     * 횟수·상한은 뺀 날짜도 포함해 센다 (RFC 5545와 같다)
     */
    static void expand(Rule r, Predicate<Occurrence> visitor) {
        RepeatFrequency f = r.frequency() == null ? RepeatFrequency.NONE : r.frequency();
        int limit = r.count() == null ? MAX_OCCURRENCES : Math.min(r.count(), MAX_OCCURRENCES);
        LocalDate first = r.startAt().toLocalDate();

        Set<DayOfWeek> weekDays = EnumSet.noneOf(DayOfWeek.class);
        if (r.days() != null) {
            weekDays.addAll(r.days());
        }
        if (weekDays.isEmpty()) {
            weekDays.add(first.getDayOfWeek());
        }
        LocalDate firstMonday = first.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        int produced = 0;
        // step: 일/주/달/해 단위 몇 번째인지. 없는 달·해는 건너뛰므로 produced와 따로 센다
        for (long step = 0; produced < limit; step++) {
            List<LocalDate> candidates = candidatesAt(f, first, firstMonday, weekDays, step);
            if (candidates == null) {
                return;
            }
            for (LocalDate date : candidates) {
                if (date.isBefore(first)) {
                    continue; // 시작 주의 시작일 이전 요일
                }
                if (r.until() != null && date.isAfter(r.until())) {
                    return;
                }
                if (produced >= limit) {
                    return;
                }
                produced++;
                if (r.exclusions().contains(date)) {
                    continue;
                }
                LocalDateTime start = date.atTime(r.startAt().toLocalTime());
                if (!visitor.test(new Occurrence(start, start.plus(r.length())))) {
                    return;
                }
            }
            if (f == RepeatFrequency.NONE) {
                return;
            }
        }
    }

    // step번째 단위의 회차 날짜들. 없는 날(31일 없는 달, 평년의 2/29)은 빈 목록. 더 이상 없으면 null
    private static List<LocalDate> candidatesAt(RepeatFrequency f, LocalDate first,
                                                LocalDate firstMonday, Set<DayOfWeek> weekDays,
                                                long step) {
        int day = first.getDayOfMonth();
        switch (f) {
            case NONE:
                return step == 0 ? List.of(first) : null;
            case DAILY:
                return List.of(first.plusDays(step));
            case WEEKLY: {
                LocalDate monday = firstMonday.plusWeeks(step);
                List<LocalDate> list = new ArrayList<>();
                for (DayOfWeek d : DayOfWeek.values()) {
                    if (weekDays.contains(d)) {
                        list.add(monday.plusDays(d.getValue() - 1L));
                    }
                }
                return list;
            }
            case MONTHLY: {
                LocalDate month = first.withDayOfMonth(1).plusMonths(step);
                return day <= month.lengthOfMonth() ? List.of(month.withDayOfMonth(day)) : List.of();
            }
            case YEARLY: {
                LocalDate month = first.withDayOfMonth(1).plusYears(step);
                return day <= month.lengthOfMonth() ? List.of(month.withDayOfMonth(day)) : List.of();
            }
            default:
                return null;
        }
    }
}
