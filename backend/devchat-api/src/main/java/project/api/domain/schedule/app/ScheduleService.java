package project.api.domain.schedule.app;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.api.domain.member.dao.MemberRepository;
import project.api.domain.member.entity.Member;
import project.api.domain.schedule.dao.ScheduleRepository;
import project.api.domain.schedule.dto.OccurrenceResponse;
import project.api.domain.schedule.dto.ScheduleRequest;
import project.api.domain.schedule.dto.ScheduleResponse;
import project.api.domain.schedule.entity.RepeatFrequency;
import project.api.domain.schedule.entity.Schedule;
import project.api.domain.schedule.entity.ScheduleDraft;
import project.api.domain.schedule.entity.ScheduleParticipant;
import project.api.global.exception.errorcode.MemberErrorCode;
import project.api.global.exception.errorcode.ScheduleErrorCode;
import project.api.global.exception.ex.MemberException;
import project.api.global.exception.ex.ScheduleException;

@Service
@Transactional(readOnly = true)
public class ScheduleService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int TITLE_MAX = 100;
    private static final int LOCATION_MAX = 200;
    private static final int MEMO_MAX = 5000;
    private static final int REMINDER_MAX_COUNT = 5;
    private static final int REMINDER_MIN = -1439;
    private static final int REMINDER_MAX = 10080;
    private static final int REPEAT_COUNT_MAX = 365;
    private static final Duration RANGE_MAX = Duration.ofDays(62);
    private static final int UPCOMING_MAX = 30;
    private static final List<Integer> DEFAULT_TIMED_REMINDER = List.of(30);
    private static final List<Integer> DEFAULT_ALL_DAY_REMINDER = List.of(900);

    private final ScheduleRepository scheduleRepository;
    private final MemberRepository memberRepository;
    private final ParticipantPolicy participantPolicy;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Autowired
    public ScheduleService(ScheduleRepository scheduleRepository, MemberRepository memberRepository,
                           ParticipantPolicy participantPolicy, ApplicationEventPublisher events) {
        this(scheduleRepository, memberRepository, participantPolicy, events, Clock.system(KST));
    }

    // 테스트에서 시각을 고정할 때 쓴다
    ScheduleService(ScheduleRepository scheduleRepository, MemberRepository memberRepository,
                    ParticipantPolicy participantPolicy, ApplicationEventPublisher events,
                    Clock clock) {
        this.scheduleRepository = scheduleRepository;
        this.memberRepository = memberRepository;
        this.participantPolicy = participantPolicy;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public ScheduleResponse create(Long me, ScheduleRequest r) {
        ScheduleDraft draft = toDraft(r);
        Member owner = memberRepository.findById(me)
            .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));
        List<Member> participants = participantPolicy.validate(me, requestedIds(me, r));

        Schedule s = Schedule.create(owner, draft, now());
        List<Long> added = s.replaceParticipants(participants);
        Schedule saved = scheduleRepository.save(s);

        if (!added.isEmpty()) {
            events.publishEvent(new ParticipantsAddedEvent(saved.getId(), me, added));
        }
        return ScheduleResponse.of(saved, me);
    }

    @Transactional
    public ScheduleResponse update(Long me, Long id, ScheduleRequest r) {
        Schedule s = findOwned(me, id);
        ScheduleDraft draft = toDraft(r);
        List<Member> participants = mergeParticipants(me, s, requestedIds(me, r));

        boolean timeChanged = s.applyChanges(draft, now());
        List<Long> added = s.replaceParticipants(participants);
        List<Long> removed = List.copyOf(s.lastRemovedParticipantIds());

        if (!added.isEmpty()) {
            events.publishEvent(new ParticipantsAddedEvent(s.getId(), me, added));
        }
        if (timeChanged) {
            events.publishEvent(new ScheduleChangedEvent(s.getId(), me));
        }
        if (!removed.isEmpty()) {
            events.publishEvent(new ScheduleCanceledEvent(s.getId(), me, s.getTitle(), removed));
        }
        return ScheduleResponse.of(s, me);
    }

    @Transactional
    public void delete(Long me, Long id) {
        Schedule s = findOwned(me, id);
        ScheduleCanceledEvent event = new ScheduleCanceledEvent(s.getId(), me, s.getTitle(),
            List.copyOf(s.participantIds()));
        scheduleRepository.delete(s);
        events.publishEvent(event);
    }

    @Transactional
    public void excludeOccurrence(Long me, Long id, LocalDate date) {
        Schedule s = findOwned(me, id);
        if (date == null || s.getRepeatFrequency() == RepeatFrequency.NONE) {
            throw new ScheduleException(ScheduleErrorCode.INVALID_SCHEDULE);
        }
        boolean already = s.getExclusions().stream()
            .anyMatch(e -> e.getOccurrenceDate().equals(date));
        if (already) {
            return;
        }
        // 그날 시작하는 회차가 있어야 뺄 수 있다
        boolean isOccurrence = RecurrenceExpander.between(s, date.atStartOfDay(),
                date.plusDays(1).atStartOfDay()).stream()
            .anyMatch(o -> o.start().toLocalDate().equals(date));
        if (!isOccurrence) {
            throw new ScheduleException(ScheduleErrorCode.INVALID_SCHEDULE);
        }
        s.exclude(date);
        events.publishEvent(new ScheduleChangedEvent(s.getId(), me));
    }

    @Transactional
    public void leave(Long me, Long id) {
        Schedule s = findVisible(me, id);
        if (s.isOwner(me)) {
            throw new ScheduleException(ScheduleErrorCode.INVALID_SCHEDULE);
        }
        s.removeParticipant(me);
    }

    public ScheduleResponse get(Long me, Long id) {
        return ScheduleResponse.of(findVisible(me, id), me);
    }

    public List<OccurrenceResponse> occurrences(Long me, LocalDateTime from, LocalDateTime to) {
        if (from == null || to == null || !to.isAfter(from)
            || Duration.between(from, to).compareTo(RANGE_MAX) > 0) {
            throw new ScheduleException(ScheduleErrorCode.INVALID_SCHEDULE);
        }
        // 하루 종일 일정의 endAt은 마지막 날 00:00이라, 그날 00:00부터 찾아야 빠지지 않는다
        LocalDateTime dayFrom = from.toLocalDate().atStartOfDay();
        List<OccurrenceResponse> result = new ArrayList<>();
        for (Schedule s : scheduleRepository.findVisibleOverlapping(me, dayFrom, to)) {
            for (Occurrence o : RecurrenceExpander.between(s, dayFrom, to)) {
                // 시간이 있는 회차는 요청 범위로 다시 거른다. 하루 종일은 그날 전체라 그대로 둔다
                if (s.isAllDay() || !o.end().isBefore(from)) {
                    result.add(OccurrenceResponse.of(s, o, me));
                }
            }
        }
        result.sort(OCCURRENCE_ORDER);
        return result;
    }

    public List<OccurrenceResponse> upcoming(Long me, int limit) {
        int n = Math.max(1, Math.min(limit, UPCOMING_MAX));
        LocalDateTime now = now();
        List<OccurrenceResponse> result = new ArrayList<>();
        for (Schedule s : scheduleRepository.findAllVisible(me)) {
            LocalDateTime after = now;
            for (int i = 0; i < n; i++) {
                Optional<Occurrence> next = RecurrenceExpander.next(s, after);
                if (next.isEmpty()) {
                    break;
                }
                result.add(OccurrenceResponse.of(s, next.get(), me));
                after = next.get().start().plusNanos(1);
            }
        }
        result.sort(OCCURRENCE_ORDER);
        return result.size() > n ? List.copyOf(result.subList(0, n)) : result;
    }

    private static final Comparator<OccurrenceResponse> OCCURRENCE_ORDER =
        Comparator.comparing(OccurrenceResponse::start)
            .thenComparing(OccurrenceResponse::scheduleId,
                Comparator.nullsLast(Comparator.naturalOrder()));

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    // 못 보는 일정은 404
    private Schedule findVisible(Long me, Long id) {
        return scheduleRepository.findById(id)
            .filter(s -> s.canView(me))
            .orElseThrow(() -> new ScheduleException(ScheduleErrorCode.NOT_FOUND));
    }

    // 볼 수는 있지만 만든 사람이 아니면 403
    private Schedule findOwned(Long me, Long id) {
        Schedule s = findVisible(me, id);
        if (!s.isOwner(me)) {
            throw new ScheduleException(ScheduleErrorCode.FORBIDDEN);
        }
        return s;
    }

    // 요청한 참여자 id. 나 자신·중복·null은 뺀다
    private static List<Long> requestedIds(Long me, ScheduleRequest r) {
        if (r.participantIds() == null) {
            return List.of();
        }
        Set<Long> ids = new LinkedHashSet<>();
        for (Long id : r.participantIds()) {
            if (id != null && !id.equals(me)) {
                ids.add(id);
            }
        }
        return List.copyOf(ids);
    }

    // 이미 있는 참여자는 그대로 두고, 새로 넣는 사람만 검사한다
    private List<Member> mergeParticipants(Long me, Schedule s, List<Long> requested) {
        Map<Long, Member> current = s.getParticipants().stream()
            .map(ScheduleParticipant::getMember)
            .collect(Collectors.toMap(Member::getId, Function.identity(), (a, b) -> a,
                LinkedHashMap::new));
        List<Long> newIds = requested.stream().filter(id -> !current.containsKey(id)).toList();
        Map<Long, Member> validated = participantPolicy.validate(me, newIds).stream()
            .collect(Collectors.toMap(Member::getId, Function.identity(), (a, b) -> a));

        List<Member> result = new ArrayList<>();
        for (Long id : requested) {
            Member m = current.containsKey(id) ? current.get(id) : validated.get(id);
            if (m != null) {
                result.add(m);
            }
        }
        return result;
    }

    // 요청을 검증하고 저장할 모양으로 맞춘다
    private static ScheduleDraft toDraft(ScheduleRequest r) {
        String title = r.title() == null ? "" : r.title().strip();
        if (title.isEmpty() || title.length() > TITLE_MAX) {
            throw invalid();
        }
        String location = blankToNull(r.location());
        if (location != null && location.length() > LOCATION_MAX) {
            throw invalid();
        }
        String memo = blankToNull(r.memo());
        if (memo != null && memo.length() > MEMO_MAX) {
            throw invalid();
        }

        if (r.startAt() == null || r.endAt() == null) {
            throw invalid();
        }
        LocalDateTime start = r.startAt();
        LocalDateTime end = r.endAt();
        if (r.allDay()) {
            // 하루 종일이면 00:00으로 맞추고 같은 날도 된다
            start = start.toLocalDate().atStartOfDay();
            end = end.toLocalDate().atStartOfDay();
            if (end.isBefore(start)) {
                throw invalid();
            }
        } else if (!end.isAfter(start)) {
            throw invalid();
        }

        RepeatFrequency freq = r.repeatFrequency() == null ? RepeatFrequency.NONE : r.repeatFrequency();
        if (r.repeatUntil() != null && r.repeatCount() != null) {
            throw invalid();
        }
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        LocalDate until = null;
        Integer count = null;
        if (freq != RepeatFrequency.NONE) {
            // 반복 없음이면 남은 반복 값은 버린다 (회차가 안 나올 수 있다)
            until = r.repeatUntil();
            count = r.repeatCount();
            if (count != null && (count < 1 || count > REPEAT_COUNT_MAX)) {
                throw invalid();
            }
            if (until != null && until.isBefore(start.toLocalDate())) {
                throw invalid();
            }
            if (freq == RepeatFrequency.WEEKLY) {
                if (r.repeatDays() != null) {
                    if (r.repeatDays().stream().anyMatch(Objects::isNull)) {
                        throw invalid();
                    }
                    days.addAll(r.repeatDays());
                }
                // 시작일도 회차로 센다 (RFC 5545와 같다)
                days.add(start.getDayOfWeek());
            }
        }

        List<Integer> reminders = reminders(r.reminderMinutes(), r.allDay());
        return new ScheduleDraft(title, location, memo, start, end, r.allDay(), freq, days,
            until, count, reminders);
    }

    private static List<Integer> reminders(List<Integer> requested, boolean allDay) {
        if (requested == null) {
            return allDay ? DEFAULT_ALL_DAY_REMINDER : DEFAULT_TIMED_REMINDER;
        }
        if (requested.stream().anyMatch(Objects::isNull)) {
            throw invalid();
        }
        List<Integer> unique = List.copyOf(new LinkedHashSet<>(requested));
        if (unique.size() > REMINDER_MAX_COUNT) {
            throw invalid();
        }
        for (int m : unique) {
            if (m < REMINDER_MIN || m > REMINDER_MAX) {
                throw invalid();
            }
        }
        return unique;
    }

    private static String blankToNull(String v) {
        if (v == null) {
            return null;
        }
        String t = v.strip();
        return t.isEmpty() ? null : t;
    }

    private static ScheduleException invalid() {
        return new ScheduleException(ScheduleErrorCode.INVALID_SCHEDULE);
    }
}
