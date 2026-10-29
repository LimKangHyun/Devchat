package project.api.domain.schedule.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import project.api.domain.member.entity.Member;

@Entity
@Table(name = "schedule")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Schedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_member_id", nullable = false)
    private Member owner;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 200)
    private String location;

    @Column(columnDefinition = "TEXT")
    private String memo;

    @Column(nullable = false)
    private LocalDateTime startAt;

    @Column(nullable = false)
    private LocalDateTime endAt;

    @Column(nullable = false)
    private boolean allDay;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private RepeatFrequency repeatFrequency = RepeatFrequency.NONE;

    // "MO,WE,FR" 문자열로 저장
    @Convert(converter = DayOfWeekSetConverter.class)
    @Column(length = 30)
    private Set<DayOfWeek> repeatDays = EnumSet.noneOf(DayOfWeek.class);

    private LocalDate repeatUntil;

    private Integer repeatCount;

    // ICS SEQUENCE용. 바뀔 때마다 올린다
    @Column(nullable = false)
    private int sequence;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "schedule", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ScheduleParticipant> participants = new ArrayList<>();

    @OneToMany(mappedBy = "schedule", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ScheduleReminder> reminders = new ArrayList<>();

    @OneToMany(mappedBy = "schedule", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ScheduleExclusion> exclusions = new ArrayList<>();

    // 마지막 replaceParticipants에서 빠진 사람
    @Transient
    private List<Long> lastRemovedParticipantIds = new ArrayList<>();

    public static Schedule create(Member owner, ScheduleDraft d, LocalDateTime now) {
        Schedule s = new Schedule();
        s.owner = owner;
        s.createdAt = now;
        s.sequence = 0;
        s.assign(d, now);
        s.syncReminders(d.reminderMinutes());
        return s;
    }

    /** 시간·반복이 바뀌었으면 true. 무엇이든 바뀌면 sequence를 올린다 */
    public boolean applyChanges(ScheduleDraft d, LocalDateTime now) {
        boolean timeChanged = !startAt.equals(d.startAt())
            || !endAt.equals(d.endAt())
            || allDay != d.allDay()
            || repeatFrequency != orNone(d.repeatFrequency())
            || !repeatDays.equals(daysOf(d))
            || !Objects.equals(repeatUntil, d.repeatUntil())
            || !Objects.equals(repeatCount, d.repeatCount());
        boolean otherChanged = !title.equals(d.title())
            || !Objects.equals(location, d.location())
            || !Objects.equals(memo, d.memo())
            || !currentReminderMinutes().equals(newReminderSet(d.reminderMinutes()));

        assign(d, now);
        syncReminders(d.reminderMinutes());
        if (timeChanged || otherChanged) {
            sequence++;
        }
        return timeChanged;
    }

    public void exclude(LocalDate occurrenceDate) {
        boolean already = exclusions.stream()
            .anyMatch(e -> e.getOccurrenceDate().equals(occurrenceDate));
        if (!already) {
            exclusions.add(new ScheduleExclusion(this, occurrenceDate));
        }
    }

    public boolean isOwner(Long memberId) {
        return owner.getId().equals(memberId);
    }

    public boolean canView(Long memberId) {
        return isOwner(memberId) || participantIds().contains(memberId);
    }

    public Set<Long> participantIds() {
        return participants.stream()
            .map(p -> p.getMember().getId())
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** 참여자를 통째로 바꾼다. 새로 추가된 id를 돌려주고, 빠진 id는 lastRemovedParticipantIds()로 꺼낸다 */
    public List<Long> replaceParticipants(List<Member> members) {
        Set<Long> current = participantIds();
        Set<Long> wanted = members.stream().map(Member::getId)
            .collect(Collectors.toCollection(LinkedHashSet::new));

        lastRemovedParticipantIds = current.stream().filter(id -> !wanted.contains(id)).toList();
        participants.removeIf(p -> !wanted.contains(p.getMember().getId()));

        List<Long> added = new ArrayList<>();
        Set<Long> have = participantIds();
        for (Member m : members) {
            if (have.add(m.getId())) {
                participants.add(new ScheduleParticipant(this, m));
                added.add(m.getId());
            }
        }
        return added;
    }

    /** replaceParticipants 직후, 같은 인스턴스에서만 유효하다 (저장되지 않는 값) */
    public List<Long> lastRemovedParticipantIds() {
        return Collections.unmodifiableList(lastRemovedParticipantIds);
    }

    public void removeParticipant(Long memberId) {
        participants.removeIf(p -> p.getMember().getId().equals(memberId));
    }

    private void assign(ScheduleDraft d, LocalDateTime now) {
        this.title = d.title();
        this.location = d.location();
        this.memo = d.memo();
        this.startAt = d.startAt();
        this.endAt = d.endAt();
        this.allDay = d.allDay();
        this.repeatFrequency = orNone(d.repeatFrequency());
        this.repeatDays = daysOf(d);
        this.repeatUntil = d.repeatUntil();
        this.repeatCount = d.repeatCount();
        this.updatedAt = now;
    }

    private static RepeatFrequency orNone(RepeatFrequency f) {
        return f == null ? RepeatFrequency.NONE : f;
    }

    private static Set<DayOfWeek> daysOf(ScheduleDraft d) {
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        if (d.repeatDays() != null) {
            days.addAll(d.repeatDays());
        }
        return days;
    }

    private Set<Integer> currentReminderMinutes() {
        return reminders.stream().map(ScheduleReminder::getMinutesBefore)
            .collect(Collectors.toSet());
    }

    private static Set<Integer> newReminderSet(List<Integer> minutes) {
        return minutes == null ? Set.of() : new LinkedHashSet<>(minutes);
    }

    // 알림 목록을 새 값과 맞춘다 (같은 값은 그대로 둔다)
    private void syncReminders(List<Integer> minutes) {
        Set<Integer> wanted = newReminderSet(minutes);
        reminders.removeIf(r -> !wanted.contains(r.getMinutesBefore()));
        Set<Integer> have = currentReminderMinutes();
        for (Integer m : wanted) {
            if (!have.contains(m)) {
                reminders.add(new ScheduleReminder(this, m));
            }
        }
    }
}
