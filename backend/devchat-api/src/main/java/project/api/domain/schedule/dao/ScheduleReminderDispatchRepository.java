package project.api.domain.schedule.dao;

import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import project.api.domain.schedule.entity.ScheduleReminderDispatch;

public interface ScheduleReminderDispatchRepository
    extends JpaRepository<ScheduleReminderDispatch, Long> {

    // 이 회차·이 알림을 이미 보냈는지
    boolean existsByScheduleIdAndOccurrenceStartAndMinutesBefore(Long scheduleId,
                                                                LocalDateTime occurrenceStart,
                                                                int minutesBefore);

    // before보다 먼저 보낸 기록을 지운다
    @Modifying
    @Query("delete from ScheduleReminderDispatch d where d.sentAt < :before")
    int deleteSentBefore(@Param("before") LocalDateTime before);
}
