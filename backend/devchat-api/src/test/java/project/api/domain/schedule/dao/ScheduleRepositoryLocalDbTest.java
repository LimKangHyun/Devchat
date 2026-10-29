package project.api.domain.schedule.dao;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import project.api.domain.member.entity.Member;
import project.api.domain.schedule.entity.RepeatFrequency;
import project.api.domain.schedule.entity.Schedule;
import project.api.domain.schedule.entity.ScheduleDraft;

// 로컬 docker MySQL(devchat)에 붙어 HQL을 실제로 실행한다. LOCAL_DB_TEST=1 일 때만 돈다. 데이터는 롤백된다
@EnabledIfEnvironmentVariable(named = "LOCAL_DB_TEST", matches = "1")
@DataJpaTest(properties = {
    "spring.datasource.url=jdbc:mysql://localhost:3306/devchat",
    "spring.datasource.username=root",
    "spring.datasource.password=root_pw",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.flyway.enabled=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ScheduleRepositoryLocalDbTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 29, 9, 0);

    @Autowired TestEntityManager em;
    @Autowired ScheduleRepository repo;

    private Member member(String name) {
        return em.persist(Member.builder().username("t_" + name).nickname(name)
            .email(name + "@t.test").profileImage("p").build());
    }

    private Schedule save(Member owner, LocalDateTime start, RepeatFrequency freq,
                          LocalDate until, List<Integer> reminders) {
        ScheduleDraft d = new ScheduleDraft("t", null, null, start, start.plusHours(1), false,
            freq, Set.of(), until, null, reminders);
        return em.persist(Schedule.create(owner, d, NOW));
    }

    private void flush() {
        em.flush();
        em.clear();
    }

    @Test
    void overlapping_nonRepeating_insideAndOutside() {
        Member me = member("a1");
        Schedule inside = save(me, LocalDateTime.of(2026, 11, 3, 10, 0), RepeatFrequency.NONE, null, List.of());
        save(me, LocalDateTime.of(2026, 12, 3, 10, 0), RepeatFrequency.NONE, null, List.of());
        save(me, LocalDateTime.of(2026, 10, 3, 10, 0), RepeatFrequency.NONE, null, List.of());
        flush();

        List<Schedule> r = repo.findVisibleOverlapping(me.getId(),
            LocalDateTime.of(2026, 11, 1, 0, 0), LocalDateTime.of(2026, 12, 1, 0, 0));

        assertThat(r).extracting(Schedule::getId).containsExactly(inside.getId());
    }

    @Test
    void overlapping_repeating_withAndWithoutUntil() {
        Member me = member("a2");
        Schedule forever = save(me, LocalDateTime.of(2026, 1, 1, 10, 0), RepeatFrequency.DAILY, null, List.of());
        Schedule untilLater = save(me, LocalDateTime.of(2026, 1, 1, 10, 0), RepeatFrequency.DAILY,
            LocalDate.of(2026, 11, 1), List.of());
        save(me, LocalDateTime.of(2026, 1, 1, 10, 0), RepeatFrequency.DAILY,
            LocalDate.of(2026, 10, 31), List.of());
        save(me, LocalDateTime.of(2026, 12, 5, 10, 0), RepeatFrequency.DAILY, null, List.of());
        flush();

        List<Schedule> r = repo.findVisibleOverlapping(me.getId(),
            LocalDateTime.of(2026, 11, 1, 0, 0), LocalDateTime.of(2026, 12, 1, 0, 0));

        assertThat(r).extracting(Schedule::getId)
            .containsExactlyInAnyOrder(forever.getId(), untilLater.getId());
    }

    @Test
    void visible_ownerAndParticipantButNotStranger() {
        Member owner = member("a3");
        Member part = member("a4");
        Member stranger = member("a5");
        Schedule s = save(owner, LocalDateTime.of(2026, 11, 3, 10, 0), RepeatFrequency.NONE, null, List.of());
        s.replaceParticipants(List.of(part));
        flush();

        assertThat(repo.findAllVisible(owner.getId())).extracting(Schedule::getId).containsExactly(s.getId());
        assertThat(repo.findAllVisible(part.getId())).extracting(Schedule::getId).containsExactly(s.getId());
        assertThat(repo.findAllVisible(stranger.getId())).isEmpty();
        assertThat(repo.findVisibleOverlapping(part.getId(),
            LocalDateTime.of(2026, 11, 1, 0, 0), LocalDateTime.of(2026, 12, 1, 0, 0)))
            .extracting(Schedule::getId).containsExactly(s.getId());
        assertThat(repo.findVisibleOverlapping(stranger.getId(),
            LocalDateTime.of(2026, 11, 1, 0, 0), LocalDateTime.of(2026, 12, 1, 0, 0))).isEmpty();
    }

    @Test
    void reminderCandidates_nonRepeatingWindow_andNoRemindersExcluded() {
        Member me = member("a6");
        LocalDateTime windowStart = LocalDateTime.of(2026, 11, 3, 12, 0);
        LocalDateTime horizon = windowStart.plusDays(7);
        Schedule inWindow = save(me, windowStart.plusDays(1), RepeatFrequency.NONE, null, List.of(30));
        Schedule justBefore = save(me, windowStart.minusHours(20), RepeatFrequency.NONE, null, List.of(-600));
        save(me, windowStart.minusDays(3), RepeatFrequency.NONE, null, List.of(30));
        save(me, horizon.plusDays(1), RepeatFrequency.NONE, null, List.of(30));
        save(me, windowStart.plusDays(1), RepeatFrequency.NONE, null, List.of());
        flush();

        List<Schedule> r = repo.findReminderCandidates(windowStart, horizon);

        assertThat(r).extracting(Schedule::getId)
            .contains(inWindow.getId(), justBefore.getId())
            .doesNotContain(-1L);
        assertThat(r).allMatch(s -> !s.getReminders().isEmpty());
        assertThat(r).noneMatch(s -> s.getStartAt().equals(windowStart.minusDays(3))
            || s.getStartAt().equals(horizon.plusDays(1)));
    }

    @Test
    void reminderCandidates_repeating_untilEdgeKeepsNextDayReminder() {
        Member me = member("a7");
        LocalDateTime windowStart = LocalDateTime.of(2026, 11, 3, 0, 30);
        LocalDateTime horizon = windowStart.plusDays(7);
        Schedule forever = save(me, LocalDateTime.of(2026, 1, 1, 10, 0), RepeatFrequency.DAILY, null, List.of(30));
        // repeatUntil 전날: -1439 알림이면 repeatUntil 당일 00:00 이후에 울릴 수 있어 남아야 한다
        Schedule edge = save(me, LocalDateTime.of(2026, 1, 1, 10, 0), RepeatFrequency.DAILY,
            LocalDate.of(2026, 11, 2), List.of(-1439));
        Schedule ended = save(me, LocalDateTime.of(2026, 1, 1, 10, 0), RepeatFrequency.DAILY,
            LocalDate.of(2026, 10, 30), List.of(30));
        Schedule noReminder = save(me, LocalDateTime.of(2026, 1, 1, 10, 0), RepeatFrequency.DAILY, null, List.of());
        flush();

        List<Long> ids = repo.findReminderCandidates(windowStart, horizon).stream().map(Schedule::getId).toList();

        assertThat(ids).contains(forever.getId(), edge.getId())
            .doesNotContain(ended.getId(), noReminder.getId());
    }
}
