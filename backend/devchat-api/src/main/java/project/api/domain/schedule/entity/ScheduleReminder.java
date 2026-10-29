package project.api.domain.schedule.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 기준 시각보다 minutesBefore분 앞서 알린다
@Entity
@Table(name = "schedule_reminder")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScheduleReminder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "schedule_id", nullable = false)
    private Schedule schedule;

    @Column(nullable = false)
    private int minutesBefore;

    public ScheduleReminder(Schedule schedule, int minutesBefore) {
        this.schedule = schedule;
        this.minutesBefore = minutesBefore;
    }
}
