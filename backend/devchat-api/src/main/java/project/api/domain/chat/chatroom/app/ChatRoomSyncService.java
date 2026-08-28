package project.api.domain.chat.chatroom.app;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.api.domain.chat.chatroom.dao.ChatRoomCheckpointRepository;
import project.api.domain.chat.chatroom.dao.ChatRoomRedisRepository;
import project.api.domain.chat.chatroom.entity.ChatRoomCheckpoint;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatRoomSyncService {

    private final ChatRoomCheckpointRepository checkpointRepository;
    private final ChatRoomRedisRepository chatRoomRedisRepository;
    private final CheckpointWriter checkpointWriter;

    /**
     * DB 기반 대상 탐색 한 사이클에서 처리할 최대 방 수.
     *
     * Redis 장기 장애 후 복구 시점처럼 대상이 수천 개로 쌓일 수 있는데,
     * 이를 한 배치에서 전부 처리하면 (FOR UPDATE + COUNT) × N 이 한꺼번에 몰려
     * 정상 서비스 쿼리까지 밀린다. 남은 대상은 다음 주기에 이어서 처리되므로
     * 복구가 늦어질 뿐 누락되지는 않는다.
     */
    private static final int RECONCILE_BATCH_SIZE = 500;

    /**
     * 스케줄러(빠른 경로): 갱신 대상 방을 찾아 checkpoint를 재계산한다.
     *
     * 평상시에는 Redis updated-set을 힌트로 쓰지만, Redis 장애 시에는
     * DB에서 직접 대상을 찾아 스케줄러가 멈추지 않도록 한다.
     * (스케줄러가 멈추면 syncedMessageId가 고정되어, 이후 재구성 시
     *  COUNT 범위가 장애 지속 시간에 비례해 커진다)
     */
    public void syncToDb() {
        Set<Long> targets = findTargetRooms();
        reconstructAll(targets, "동기화");
    }

    /**
     * 안전망: Redis를 보지 않고 DB만으로 갱신 대상을 찾는다.
     *
     * updated-set에 기록되지 못한 방(AFTER_COMMIT 리스너 실행 전 크래시 등)은
     * Redis 경로에서는 영원히 발견되지 않으므로, SoT인 DB를 직접 훑어 회수한다.
     *
     * Redis 다운 시에는 syncToDb()의 폴백과 대상이 겹치지만, reconstruct는 멱등이라
     * (체크포인트가 이미 전진했으면 COUNT 없이 종료) 중복 집계로 이어지지 않는다.
     */
    public void reconcileFromDb() {
        Set<Long> targets = findStaleRoomsFromDb();
        reconstructAll(targets, "정합성 보정");
    }

    private void reconstructAll(Set<Long> targets, String label) {
        if (targets.isEmpty()) return;

        int success = 0;
        for (Long roomId : targets) {
            try {
                checkpointWriter.reconstruct(roomId);
                success++;
            } catch (Exception e) {
                log.error("checkpoint {} 실패 - roomId={}", label, roomId, e);
            }
        }
        log.info("checkpoint {} 완료 - {}/{}개 채팅방", label, success, targets.size());
    }

    private Set<Long> findTargetRooms() {
        try {
            return chatRoomRedisRepository.getAndClearUpdatedRooms().stream()
                    .map(Long::valueOf)
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            log.warn("Redis updated-set 조회 실패 - DB 기반 대상 탐색으로 전환");
            return findStaleRoomsFromDb();
        }
    }

    private Set<Long> findStaleRoomsFromDb() {
        return Set.copyOf(checkpointRepository.findStaleRoomIds(
                PageRequest.of(0, RECONCILE_BATCH_SIZE)));
    }

    @Transactional(readOnly = true)
    public Map<Long, Long> getCumulativeCounts(List<Long> roomIds) {
        if (roomIds == null || roomIds.isEmpty()) return Map.of();
        return checkpointRepository.findByRoomIdIn(roomIds).stream()
                .collect(Collectors.toMap(
                        ChatRoomCheckpoint::getRoomId,
                        ChatRoomCheckpoint::getCumulativeCount));
    }

    @Transactional
    public void deleteByRoomId(Long roomId) {
        checkpointRepository.deleteByRoomId(roomId);
    }
}