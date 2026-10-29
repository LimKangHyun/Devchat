package project.api.domain.schedule.entity;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

// 일정 만들기·고치기 입력값. 하루 종일이면 startAt은 첫날 00:00, endAt은 마지막 날 00:00 (끝 날짜 포함)
public record ScheduleDraft(
    String title,
    String location,
    String memo,
    LocalDateTime startAt,
    LocalDateTime endAt,
    boolean allDay,
    RepeatFrequency repeatFrequency,
    Set<DayOfWeek> repeatDays,
    LocalDate repeatUntil,
    Integer repeatCount,
    List<Integer> reminderMinutes
) {
}
