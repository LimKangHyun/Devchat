package project.api.domain.schedule.dto;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import project.api.domain.member.entity.Member;
import project.api.domain.schedule.entity.RepeatFrequency;
import project.api.domain.schedule.entity.Schedule;
import project.api.domain.schedule.entity.ScheduleExclusion;
import project.api.domain.schedule.entity.ScheduleParticipant;
import project.api.domain.schedule.entity.ScheduleReminder;

public record ScheduleResponse(
    Long id,
    String title,
    String location,
    String memo,
    LocalDateTime startAt,
    LocalDateTime endAt,
    boolean allDay,
    RepeatFrequency repeatFrequency,
    List<DayOfWeek> repeatDays,
    LocalDate repeatUntil,
    Integer repeatCount,
    List<Integer> reminderMinutes,
    List<LocalDate> exclusions,
    MemberBrief owner,
    List<MemberBrief> participants,
    boolean mine
) {

    public record MemberBrief(Long id, String nickname, String profileImg) {

        public static MemberBrief of(Member m) {
            return new MemberBrief(m.getId(), m.getNickname(), m.getProfileImage());
        }
    }

    public static ScheduleResponse of(Schedule s, Long me) {
        return new ScheduleResponse(
            s.getId(),
            s.getTitle(),
            s.getLocation(),
            s.getMemo(),
            s.getStartAt(),
            s.getEndAt(),
            s.isAllDay(),
            s.getRepeatFrequency(),
            s.getRepeatDays().stream().sorted().toList(),
            s.getRepeatUntil(),
            s.getRepeatCount(),
            s.getReminders().stream().map(ScheduleReminder::getMinutesBefore).sorted().toList(),
            s.getExclusions().stream().map(ScheduleExclusion::getOccurrenceDate).sorted().toList(),
            MemberBrief.of(s.getOwner()),
            s.getParticipants().stream().map(ScheduleParticipant::getMember)
                .map(MemberBrief::of).toList(),
            s.isOwner(me)
        );
    }
}
