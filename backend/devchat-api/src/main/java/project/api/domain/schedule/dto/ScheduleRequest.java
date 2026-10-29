package project.api.domain.schedule.dto;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import project.api.domain.schedule.entity.RepeatFrequency;

// reminderMinutes가 null이면 기본값, 빈 배열이면 알림 없음
public record ScheduleRequest(
    String title,
    String location,
    String memo,
    LocalDateTime startAt,
    LocalDateTime endAt,
    boolean allDay,
    RepeatFrequency repeatFrequency,
    List<DayOfWeek> repeatDays,
    LocalDate repeatUntil,
    Integer repeatCount,
    List<Integer> reminderMinutes,
    List<Long> participantIds
) {
}
