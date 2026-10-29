package project.api.domain.schedule.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 보낸 리마인더 기록. (일정, 회차 시작, minutesBefore)당 한 번만 보내려고 유니크로 막는다
@Entity
@Table(name = "schedule_reminder_dispatch",
    uniqueConstraints = @UniqueConstraint(name = "uk_schedule_dispatch",
        columnNames = {"schedule_id", "occurrence_start", "minutes_before"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScheduleReminderDispatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "schedule_id", nullable = false)
    private Long scheduleId;

    @Column(name = "occurrence_start", nullable = false)
    private LocalDateTime occurrenceStart;

    @Column(name = "minutes_before", nullable = false)
    private int minutesBefore;

    @Column(nullable = false)
    private LocalDateTime sentAt;

    public ScheduleReminderDispatch(Long scheduleId, LocalDateTime occurrenceStart,
                                    int minutesBefore, LocalDateTime sentAt) {
        this.scheduleId = scheduleId;
        this.occurrenceStart = occurrenceStart;
        this.minutesBefore = minutesBefore;
        this.sentAt = sentAt;
    }
}
