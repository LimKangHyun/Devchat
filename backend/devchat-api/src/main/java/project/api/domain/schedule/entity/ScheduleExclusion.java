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
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 반복 일정에서 "이번만 빼기"한 날짜
@Entity
@Table(name = "schedule_exclusion",
    uniqueConstraints = @UniqueConstraint(name = "uk_schedule_exclusion",
        columnNames = {"schedule_id", "occurrence_date"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScheduleExclusion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "schedule_id", nullable = false)
    private Schedule schedule;

    @Column(nullable = false)
    private LocalDate occurrenceDate;

    public ScheduleExclusion(Schedule schedule, LocalDate occurrenceDate) {
        this.schedule = schedule;
        this.occurrenceDate = occurrenceDate;
    }
}
