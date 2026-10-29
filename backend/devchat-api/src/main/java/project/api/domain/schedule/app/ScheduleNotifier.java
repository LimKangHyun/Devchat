package project.api.domain.schedule.app;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import project.api.domain.member.dao.MemberRepository;
import project.api.domain.member.entity.Member;
import project.api.domain.notification.UserNotificationSender;
import project.api.domain.notification.app.NotificationService;
import project.api.domain.notification.dto.NotificationDto;
import project.api.domain.notification.entity.Notification;
import project.api.domain.notification.entity.NotificationType;
import project.api.domain.schedule.dao.ScheduleRepository;
import project.api.domain.schedule.entity.Schedule;

/**
 * 일정 이벤트를 알림(저장 + 실시간/웹 푸시)으로 바꾼다.
 * 이벤트는 서비스 트랜잭션이 커밋된 뒤에 받고, 저장은 새 트랜잭션에서 한다.
 * 알림 실패가 일정 저장에 영향을 주지 않도록 예외는 로그만 남긴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduleNotifier {

    private static final DateTimeFormatter TIMED = DateTimeFormatter.ofPattern("M월 d일 (E) a h:mm", Locale.KOREAN);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN);

    private final NotificationService notificationService;
    private final UserNotificationSender notificationSender;
    private final MemberRepository memberRepository;
    private final ScheduleRepository scheduleRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onParticipantsAdded(ParticipantsAddedEvent e) {
        try {
            Member actor = memberRepository.findById(e.actorId()).orElse(null);
            Schedule s = scheduleRepository.findById(e.scheduleId()).orElse(null);
            if (actor == null || s == null) return;
            String text = actor.getNickname() + "님이 '" + s.getTitle() + "' 일정에 추가했어요";
            notifyAll(memberRepository.findAllById(e.memberIds()), actor,
                NotificationType.SCHEDULE_ADDED, s.getId(), text);
        } catch (Exception ex) {
            log.warn("[일정 알림] 추가 알림 실패 scheduleId={}: {}", e.scheduleId(), ex.getMessage());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onChanged(ScheduleChangedEvent e) {
        try {
            Member actor = memberRepository.findById(e.actorId()).orElse(null);
            Schedule s = scheduleRepository.findById(e.scheduleId()).orElse(null);
            if (actor == null || s == null) return;
            List<Member> receivers = s.getParticipants().stream().map(p -> p.getMember()).toList();
            notifyAll(receivers, actor, NotificationType.SCHEDULE_CHANGED, s.getId(),
                "'" + s.getTitle() + "' 일정이 바뀌었어요");
        } catch (Exception ex) {
            log.warn("[일정 알림] 변경 알림 실패 scheduleId={}: {}", e.scheduleId(), ex.getMessage());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onCanceled(ScheduleCanceledEvent e) {
        if (e.memberIds() == null || e.memberIds().isEmpty()) return;
        try {
            Member actor = memberRepository.findById(e.actorId()).orElse(null);
            if (actor == null) return;
            // 일정이 이미 지워졌을 수 있어 제목은 이벤트에서 가져온다
            notifyAll(memberRepository.findAllById(e.memberIds()), actor,
                NotificationType.SCHEDULE_CANCELED, e.scheduleId(),
                "'" + e.title() + "' 일정이 취소됐어요");
        } catch (Exception ex) {
            log.warn("[일정 알림] 취소 알림 실패 scheduleId={}: {}", e.scheduleId(), ex.getMessage());
        }
    }

    /** 리마인더. 만든 사람과 참여자 모두에게, 보낸 사람은 만든 사람으로 보낸다 */
    @Transactional
    public void notifyReminder(Schedule s, Occurrence o) {
        String when = s.isAllDay()
            ? o.start().format(DAY) + " 하루 종일"
            : o.start().format(TIMED);
        String text = "'" + s.getTitle() + "' " + when;

        List<Member> receivers = new ArrayList<>();
        receivers.add(s.getOwner());
        s.getParticipants().forEach(p -> receivers.add(p.getMember()));
        notifyAll(receivers, s.getOwner(), NotificationType.SCHEDULE_REMINDER, s.getId(), text, false);
    }

    private void notifyAll(Iterable<Member> receivers, Member actor, NotificationType type,
                           Long scheduleId, String text) {
        notifyAll(receivers, actor, type, scheduleId, text, true);
    }

    private void notifyAll(Iterable<Member> receivers, Member actor, NotificationType type,
                           Long scheduleId, String text, boolean skipActor) {
        for (Member receiver : receivers) {
            if (skipActor && receiver.getId().equals(actor.getId())) continue;
            Notification saved = notificationService.saveNotification(
                Notification.ofSchedule(receiver, actor, type, scheduleId, text));
            notificationSender.send(receiver.getUsername(), NotificationDto.ofNotification(saved));
        }
    }
}
