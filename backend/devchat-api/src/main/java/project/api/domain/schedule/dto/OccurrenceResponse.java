package project.api.domain.schedule.dto;

import java.time.LocalDateTime;
import project.api.domain.schedule.app.Occurrence;
import project.api.domain.schedule.entity.RepeatFrequency;
import project.api.domain.schedule.entity.Schedule;

// 달력에 그릴 회차 하나. 하루 종일이면 end는 마지막 날 00:00
public record OccurrenceResponse(
    Long scheduleId,
    String title,
    LocalDateTime start,
    LocalDateTime end,
    boolean allDay,
    boolean recurring,
    boolean mine
) {

    public static OccurrenceResponse of(Schedule s, Occurrence o, Long me) {
        return new OccurrenceResponse(s.getId(), s.getTitle(), o.start(), o.end(), s.isAllDay(),
            s.getRepeatFrequency() != RepeatFrequency.NONE, s.isOwner(me));
    }
}
