package project.api.domain.schedule.app;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import project.api.domain.schedule.dao.ScheduleReminderDispatchRepository;
import project.api.domain.schedule.dao.ScheduleRepository;
import project.api.domain.schedule.entity.Schedule;
import project.api.domain.schedule.entity.ScheduleReminder;
import project.api.domain.schedule.entity.ScheduleReminderDispatch;

/**
 * 1분마다 보낼 때가 된 일정 리마인더를 보낸다.
 * 보낼 시각이 (now - 10분, now]인 것만 보낸다. 잠깐 멈췄다 켜져도 10분까지는 따라잡고, 그보다 오래된 건 버린다.
 * 서버 두 대가 같이 돌아도 한 번만 가도록 발송 기록의 유니크 키를 먼저 잡은 쪽만 보낸다.
 */
@Slf4j
@Component
public class ScheduleReminderJob {

    static final int WINDOW_MINUTES = 10;
    static final int MAX_MINUTES_BEFORE = 10080;
    static final int KEEP_DAYS = 30;
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final ScheduleRepository scheduleRepository;
    private final ScheduleReminderDispatchRepository dispatchRepository;
    private final ScheduleNotifier notifier;
    // 후보 읽기 (지연 로딩 컬렉션을 여기서 읽는다)
    private final TransactionTemplate readTx;
    // 발송 기록·알림은 건마다 따로 커밋한다
    private final TransactionTemplate newTx;

    public ScheduleReminderJob(ScheduleRepository scheduleRepository,
                               ScheduleReminderDispatchRepository dispatchRepository,
                               ScheduleNotifier notifier,
                               PlatformTransactionManager txManager) {
        this.scheduleRepository = scheduleRepository;
        this.dispatchRepository = dispatchRepository;
        this.notifier = notifier;
        this.readTx = new TransactionTemplate(txManager);
        this.readTx.setReadOnly(true);
        this.newTx = new TransactionTemplate(txManager);
        this.newTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // 보낼 리마인더 하나
    record Due(Long scheduleId, Occurrence occurrence, int minutesBefore) {
    }

    @Scheduled(fixedDelay = 60_000)
    public void tick() {
        run(LocalDateTime.now(SEOUL));
    }

    public void run(LocalDateTime now) {
        LocalDateTime windowStart = now.minusMinutes(WINDOW_MINUTES);
        List<Due> due = readTx.execute(status -> findDue(windowStart, now));
        if (due == null) return;
        for (Due d : due) {
            send(d, now);
        }
    }

    private List<Due> findDue(LocalDateTime windowStart, LocalDateTime now) {
        // 가장 긴 알림(7일)까지 볼 수 있게 넉넉히 가져온다
        LocalDateTime horizon = now.plusMinutes(MAX_MINUTES_BEFORE + 1L);
        List<Due> result = new ArrayList<>();
        for (Schedule s : scheduleRepository.findReminderCandidates(windowStart, horizon)) {
            try {
                collect(s, windowStart, now, result);
            } catch (Exception ex) {
                log.warn("[일정 리마인더] 회차 계산 실패 scheduleId={}", s.getId(), ex);
            }
        }
        return result;
    }

    private void collect(Schedule s, LocalDateTime windowStart, LocalDateTime now, List<Due> out) {
        List<Integer> minutes = s.getReminders().stream().map(ScheduleReminder::getMinutesBefore).toList();
        if (minutes.isEmpty()) return;
        int min = minutes.stream().mapToInt(Integer::intValue).min().getAsInt();
        int max = minutes.stream().mapToInt(Integer::intValue).max().getAsInt();
        // between의 끝은 포함하지 않으므로 1분 더 본다 (보낼 시각 == now인 회차)
        List<Occurrence> occurrences = RecurrenceExpander.between(s,
            windowStart.plusMinutes(min), now.plusMinutes(max + 1L));
        for (Occurrence o : occurrences) {
            LocalDateTime anchor = s.isAllDay() ? o.start().toLocalDate().atStartOfDay() : o.start();
            for (int m : minutes) {
                LocalDateTime sendAt = anchor.minusMinutes(m);
                if (sendAt.isAfter(windowStart) && !sendAt.isAfter(now)) {
                    out.add(new Due(s.getId(), o, m));
                }
            }
        }
    }

    private void send(Due d, LocalDateTime now) {
        try {
            // 이미 보낸 건 넣어 보지도 않는다 (매분 중복 키 오류 로그가 쌓인다)
            Boolean inserted = newTx.execute(status -> {
                if (dispatchRepository.existsByScheduleIdAndOccurrenceStartAndMinutesBefore(
                    d.scheduleId(), d.occurrence().start(), d.minutesBefore())) {
                    return false;
                }
                dispatchRepository.saveAndFlush(
                    new ScheduleReminderDispatch(d.scheduleId(), d.occurrence().start(), d.minutesBefore(), now));
                return true;
            });
            if (!Boolean.TRUE.equals(inserted)) {
                return;
            }
        } catch (DataIntegrityViolationException dup) {
            // 다른 서버가 이미 보냈다
            log.debug("[일정 리마인더] 이미 보냄 scheduleId={} start={} m={}",
                d.scheduleId(), d.occurrence().start(), d.minutesBefore());
            return;
        } catch (Exception ex) {
            log.warn("[일정 리마인더] 발송 기록 실패 scheduleId={}", d.scheduleId(), ex);
            return;
        }
        try {
            // 참여자 등 지연 로딩을 위해 새 트랜잭션에서 다시 읽는다
            newTx.executeWithoutResult(status -> scheduleRepository.findById(d.scheduleId())
                .ifPresent(s -> notifier.notifyReminder(s, d.occurrence())));
        } catch (Exception ex) {
            log.warn("[일정 리마인더] 알림 실패 scheduleId={}", d.scheduleId(), ex);
        }
    }

    @Scheduled(cron = "0 20 4 * * *", zone = "Asia/Seoul")
    public void deleteOldDispatches() {
        deleteOldDispatches(LocalDateTime.now(SEOUL));
    }

    void deleteOldDispatches(LocalDateTime now) {
        Integer deleted = newTx.execute(status -> dispatchRepository.deleteSentBefore(now.minusDays(KEEP_DAYS)));
        if (deleted != null && deleted > 0) {
            log.info("[일정 리마인더] {}일 지난 발송 기록 {}건 삭제", KEEP_DAYS, deleted);
        }
    }
}
