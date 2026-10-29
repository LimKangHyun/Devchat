package project.api.domain.schedule.dao;

import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import project.api.domain.schedule.entity.ScheduleReminderDispatch;

public interface ScheduleReminderDispatchRepository
    extends JpaRepository<ScheduleReminderDispatch, Long> {

    // 이 회차·이 알림을 이미 보냈는지.
    // 메서드 이름으로 만들면 끝의 "Before"를 비교 조건으로 읽어 앱이 뜨지 않으므로 쿼리를 직접 쓴다
    @Query("""
        select count(d) > 0 from ScheduleReminderDispatch d
        where d.scheduleId = :scheduleId
          and d.occurrenceStart = :occurrenceStart
          and d.minutesBefore = :minutesBefore
        """)
    boolean alreadySent(@Param("scheduleId") Long scheduleId,
                        @Param("occurrenceStart") LocalDateTime occurrenceStart,
                        @Param("minutesBefore") int minutesBefore);

    // before보다 먼저 보낸 기록을 지운다
    @Modifying
    @Query("delete from ScheduleReminderDispatch d where d.sentAt < :before")
    int deleteSentBefore(@Param("before") LocalDateTime before);
}
