package project.api.domain.schedule.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import project.api.domain.member.entity.Member;

class ScheduleTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 29, 9, 0);
    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 3, 15, 0);

    private Member member(Long id) {
        return Member.builder().id(id).username("u" + id).nickname("n" + id).profileImage("p").build();
    }

    private ScheduleDraft draft(String title, LocalDateTime start, RepeatFrequency freq) {
        return new ScheduleDraft(title, null, null, start, start.plusHours(1), false, freq,
            Set.of(), null, null, List.of(30));
    }

    private Schedule schedule() {
        return Schedule.create(member(1L), draft("회의", START, RepeatFrequency.NONE), NOW);
    }

    @Test
    void applyChanges_timeChange_returnsTrueAndBumpsSequence() {
        Schedule s = schedule();
        int before = s.getSequence();

        boolean timeChanged = s.applyChanges(draft("회의", START.plusHours(2), RepeatFrequency.NONE), NOW);

        assertThat(timeChanged).isTrue();
        assertThat(s.getSequence()).isEqualTo(before + 1);
        assertThat(s.getStartAt()).isEqualTo(START.plusHours(2));
    }

    @Test
    void applyChanges_titleOnly_returnsFalseButBumpsSequence() {
        Schedule s = schedule();
        int before = s.getSequence();

        boolean timeChanged = s.applyChanges(draft("새 제목", START, RepeatFrequency.NONE), NOW);

        assertThat(timeChanged).isFalse();
        assertThat(s.getSequence()).isEqualTo(before + 1);
        assertThat(s.getTitle()).isEqualTo("새 제목");
    }

    @Test
    void applyChanges_repeatChange_returnsTrue() {
        Schedule s = schedule();

        boolean timeChanged = s.applyChanges(
            new ScheduleDraft("회의", null, null, START, START.plusHours(1), false,
                RepeatFrequency.WEEKLY, Set.of(DayOfWeek.MONDAY), LocalDate.of(2026, 12, 1), null,
                List.of(30)), NOW);

        assertThat(timeChanged).isTrue();
    }

    @Test
    void replaceParticipants_returnsAddedAndRemembersRemoved() {
        Schedule s = schedule();
        s.replaceParticipants(List.of(member(1L), member(2L)));

        List<Long> added = s.replaceParticipants(List.of(member(2L), member(3L)));

        assertThat(added).containsExactly(3L);
        assertThat(s.lastRemovedParticipantIds()).containsExactly(1L);
        assertThat(s.participantIds()).containsExactlyInAnyOrder(2L, 3L);
    }

    @Test
    void canView_ownerOrParticipantOnly() {
        Schedule s = schedule();
        s.replaceParticipants(List.of(member(2L)));

        assertThat(s.canView(1L)).isTrue();
        assertThat(s.canView(2L)).isTrue();
        assertThat(s.canView(3L)).isFalse();
        assertThat(s.isOwner(1L)).isTrue();
        assertThat(s.isOwner(2L)).isFalse();
    }

    @Test
    void exclude_andRemoveParticipant() {
        Schedule s = schedule();
        s.replaceParticipants(List.of(member(2L)));

        s.exclude(LocalDate.of(2026, 11, 3));
        s.removeParticipant(2L);

        assertThat(s.getExclusions()).hasSize(1);
        assertThat(s.participantIds()).isEmpty();
    }
}
