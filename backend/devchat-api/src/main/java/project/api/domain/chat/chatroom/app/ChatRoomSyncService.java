package project.api.domain.chat.chatroom.app;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.api.domain.chat.chatmessage.dao.ChatMessageRepository;
import project.api.domain.chat.chatroom.dao.ChatRoomCheckpointRepository;
import project.api.domain.chat.chatroom.dao.ChatRoomRedisRepository;
import project.api.domain.chat.chatroom.entity.ChatRoomCheckpoint;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatRoomSyncService {

    private final ChatRoomCheckpointRepository checkpointRepository;
    private final ChatRoomRedisRepository chatRoomRedisRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final CheckpointWriter checkpointWriter;

    private static final int LOOKBACK_MINUTES = 6;

    @Value("${chat.checkpoint.watermark-lag-seconds:5}")
    private long watermarkLagSeconds;

    public void syncToDb() {
        Set<Long> targets;
        try {
            targets = chatRoomRedisRepository.getAndClearUpdatedRooms().stream()
                .map(Long::valueOf)
                .collect(Collectors.toSet());
        } catch (Exception e) {
            log.warn("Redis updated-set 조회 실패 - 이번 사이클 건너뜀 (안전망이 회수)");
            return;
        }
        reconstructAll(targets, "동기화");
    }

    public void reconcileFromDb() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime from = now.minusMinutes(LOOKBACK_MINUTES);
        LocalDateTime to = now.minusSeconds(watermarkLagSeconds);

        Set<Long> targets = Set.copyOf(
            chatMessageRepository.findDistinctRoomIdsByCreatedAtBetween(from, to));

        log.debug("정합성 보정 대상 조회 - 구간=[{} ~ {}], 대상={}개", from, to, targets.size());
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