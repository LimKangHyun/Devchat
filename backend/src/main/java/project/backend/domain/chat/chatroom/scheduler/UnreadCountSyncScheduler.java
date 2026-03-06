package project.backend.domain.chat.chatroom.scheduler;

import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import project.backend.domain.chat.chatroom.dao.ChatParticipantRepository;

@Slf4j
@Component
@RequiredArgsConstructor
public class UnreadCountSyncScheduler {

    private final RedisTemplate<String, String> redisTemplate;
    private final ChatParticipantRepository chatParticipantRepository;

    @Scheduled(cron = "0 0 * * * *") // 1시간마다
    @Transactional
    public void syncUnreadCountToDB() {
        Set<String> keys = redisTemplate.keys("unread:*:*");
        if (keys == null || keys.isEmpty()) {
            return;
        }

        for (String key : keys) {
            try {
                // GETDEL로 원자적으로 읽고 삭제
                String value = redisTemplate.opsForValue().getAndDelete(key);
                if (value == null) {
                    continue;
                }

                long count = Long.parseLong(value);
                if (count == 0) {
                    continue;
                }

                // key = "unread:{roomId}:{memberId}"
                String[] parts = key.split(":");
                Long roomId = Long.parseLong(parts[1]);
                Long memberId = Long.parseLong(parts[2]);

                chatParticipantRepository
                    .findByChatRoomIdAndParticipantIdAndIsActiveTrue(roomId, memberId)
                    .ifPresent(p -> p.addUnreadCount(count));

                log.info("unread count 동기화 완료 - roomId: {}, memberId: {}, count: {}",
                    roomId, memberId, count);

            } catch (Exception e) {
                log.error("unread count 동기화 실패: {}", key, e);
            }
        }
    }
}
