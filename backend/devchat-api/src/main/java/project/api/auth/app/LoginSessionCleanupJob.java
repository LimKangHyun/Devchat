package project.api.auth.app;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import project.api.auth.dao.LoginSessionRepository;

/** 만료되거나 끊긴 지 하루 지난 로그인 세션을 매일 새벽에 지운다 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginSessionCleanupJob {

    private final LoginSessionRepository loginSessionRepository;

    @Scheduled(cron = "0 10 4 * * *", zone = "Asia/Seoul")
    @Transactional
    public void deleteOldSessions() {
        int deleted = loginSessionRepository.deleteExpiredOrRevokedBefore(LocalDateTime.now().minusDays(1));
        if (deleted > 0) log.info("[세션 정리] 지난 로그인 세션 {}건 삭제", deleted);
    }
}
