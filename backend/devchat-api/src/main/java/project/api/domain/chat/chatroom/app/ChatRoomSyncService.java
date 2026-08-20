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
     * 스케줄러: Redis updated-set은 "어떤 방을 볼지" 힌트로만 쓰고,
     * 실제 값은 항상 DB에서 재계산한다.
     */
    public void syncToDb() {
        Set<String> updatedRoomIds = chatRoomRedisRepository.getAndClearUpdatedRooms();
        if (updatedRoomIds.isEmpty()) return;

        for (String id : updatedRoomIds) {
            Long roomId = Long.valueOf(id);
            try {
                checkpointWriter.reconstruct(roomId);   // 프록시 경유 → 트랜잭션 정상 적용
            } catch (Exception e) {
                log.error("checkpoint 동기화 실패 - roomId={}", roomId, e);
            }
        }
        log.info("checkpoint 동기화 완료 - {}개 채팅방", updatedRoomIds.size());
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