package project.api.domain.notification.app;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import project.api.domain.notification.dao.NotificationRepository;
import project.api.domain.notification.entity.NotificationType;

/**
 * 30일 지난 알림을 매일 새벽에 지운다.
 * 아직 처리하지 않은 친구 요청은 남긴다 (지우면 수락·거절 버튼이 사라진다).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationCleanupJob {

    static final int KEEP_DAYS = 30;

    private final NotificationRepository notificationRepository;

    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    @Transactional
    public void deleteOldNotifications() {
        LocalDateTime before = LocalDateTime.now().minusDays(KEEP_DAYS);
        int deleted = notificationRepository.deleteOlderThan(before, NotificationType.FRIEND_REQUESTED);
        if (deleted > 0) log.info("[알림 정리] {}일 지난 알림 {}건 삭제", KEEP_DAYS, deleted);
    }
}
