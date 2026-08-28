package project.api.domain.chat.chatroom.app;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatRoomCheckpointScheduler {

    private final ChatRoomSyncService chatRoomSyncService;

    /**
     * 빠른 경로: Redis updated-set을 힌트로 삼아 변경된 방만 재계산한다.
     * 대다수의 갱신은 여기서 처리된다.
     */
    @Scheduled(fixedDelay = 30_000)
    public void syncCheckpoints() {
        try {
            chatRoomSyncService.syncToDb();
        } catch (Exception e) {
            log.error("checkpoint 동기화 배치 실패", e);
        }
    }

    /**
     * 안전망: Redis를 전혀 보지 않고 DB만으로 갱신 대상을 찾아 재계산한다.
     *
     * updated-set 기록은 AFTER_COMMIT 리스너가 수행하는데, 커밋 직후 서버가 죽으면
     * 이 기록이 누락될 수 있다. 이 경우 Redis 자체는 정상이라 syncToDb()의 예외 폴백에
     * 걸리지 않고, 해당 방에 새 메시지가 더 오지 않으면 영구히 갱신 대상에서 빠진다.
     *
     * DB(SoT)만 보는 이 경로가 그 누락을 회수한다. Redis 상태를 조건으로 걸지 않는 이유는
     * 이 실패가 "Redis는 정상인데 기록만 누락된" 상황이기 때문이다.
     *
     * 전체 방을 스캔하므로 빠른 경로보다 비용이 크고, 그만큼 주기를 길게 잡아
     * 평상시 부하를 억제한다. 이 주기가 곧 "기록 누락이 복구되기까지의 최대 지연"이다.
     */
    @Scheduled(fixedDelay = 300_000)
    public void reconcileCheckpoints() {
        try {
            chatRoomSyncService.reconcileFromDb();
        } catch (Exception e) {
            log.error("checkpoint 정합성 보정 배치 실패", e);
        }
    }
}