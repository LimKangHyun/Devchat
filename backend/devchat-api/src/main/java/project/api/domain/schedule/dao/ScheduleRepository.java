package project.api.domain.schedule.dao;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import project.api.domain.schedule.entity.Schedule;

public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

    // 내가 만들었거나 참여하는 일정 중 [from, to)와 겹칠 수 있는 것. 반복 일정은 후보만 거른다
    @Query("""
        select distinct s from Schedule s
        left join s.participants p
        where (s.owner.id = :memberId or p.member.id = :memberId)
          and ((s.repeatFrequency = project.api.domain.schedule.entity.RepeatFrequency.NONE
                and s.startAt < :to and s.endAt >= :from)
            or (s.repeatFrequency <> project.api.domain.schedule.entity.RepeatFrequency.NONE
                and s.startAt < :to
                and (s.repeatUntil is null or s.repeatUntil >= cast(:from as date))))
        """)
    List<Schedule> findVisibleOverlapping(@Param("memberId") Long memberId,
                                          @Param("from") LocalDateTime from,
                                          @Param("to") LocalDateTime to);

    @Query("""
        select distinct s from Schedule s
        left join s.participants p
        where s.owner.id = :memberId or p.member.id = :memberId
        """)
    List<Schedule> findAllVisible(@Param("memberId") Long memberId);

    // 알림이 있는 일정 중 리마인더를 볼 만한 것. 반복 일정은 끝나지 않은 것만
    @Query("""
        select distinct s from Schedule s
        where exists (select 1 from ScheduleReminder r where r.schedule = s)
          and ((s.repeatFrequency = project.api.domain.schedule.entity.RepeatFrequency.NONE
                and s.startAt >= :windowStart - 1 day and s.startAt <= :horizon)
            or (s.repeatFrequency <> project.api.domain.schedule.entity.RepeatFrequency.NONE
                and (s.repeatUntil is null or s.repeatUntil >= cast(:windowStart - 1 day as date))))
        """)
    List<Schedule> findReminderCandidates(@Param("windowStart") LocalDateTime windowStart,
                                          @Param("horizon") LocalDateTime horizon);
}
