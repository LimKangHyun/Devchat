package project.api.domain.schedule.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import project.api.domain.schedule.entity.ScheduleReminderDispatch;

public interface ScheduleReminderDispatchRepository
    extends JpaRepository<ScheduleReminderDispatch, Long> {
}
