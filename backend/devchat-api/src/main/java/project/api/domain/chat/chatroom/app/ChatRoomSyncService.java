package project.api.domain.chat.chatroom.app;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
     * 스케줄러: 갱신 대상 방을 찾아 checkpoint를 재계산한다.
     *
     * 평상시에는 Redis updated-set을 힌트로 쓰지만, Redis 장애 시에는
     * DB에서 직접 대상을 찾아 스케줄러가 멈추지 않도록 한다.
     * (스케줄러가 멈추면 syncedMessageId가 고정되어, 이후 재구성 시
     *  COUNT 범위가 장애 지속 시간에 비례해 커진다)
     */
    public void syncToDb() {
        Set<Long> targets = findTargetRooms();
        if (targets.isEmpty()) return;

        int success = 0;
        for (Long roomId : targets) {
            try {
                checkpointWriter.reconstruct(roomId);
                success++;
            } catch (Exception e) {
                log.error("checkpoint 동기화 실패 - roomId={}", roomId, e);
            }
        }
        log.info("checkpoint 동기화 완료 - {}/{}개 채팅방", success, targets.size());
    }

    private Set<Long> findTargetRooms() {
        try {
            return chatRoomRedisRepository.getAndClearUpdatedRooms().stream()
                    .map(Long::valueOf)
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            log.warn("Redis updated-set 조회 실패 - DB 기반 대상 탐색으로 전환");
            return Set.copyOf(checkpointRepository.findStaleRoomIds());
        }
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