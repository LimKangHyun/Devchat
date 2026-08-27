package project.api.domain.chat.chatroom.app;

import java.time.LocalDateTime;
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
public class CheckpointWriter {

    private final ChatRoomCheckpointRepository checkpointRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatRoomRedisRepository chatRoomRedisRepository;

    @Value("${chat.checkpoint.watermark-lag-seconds:5}")
    private long watermarkLagSeconds;

    @Transactional
    public Long reconstruct(Long roomId) {
        ChatRoomCheckpoint cp = checkpointRepository.findByRoomIdForUpdate(roomId)
            .orElseGet(() -> checkpointRepository.save(new ChatRoomCheckpoint(roomId)));

        Long syncedId = cp.getSyncedMessageId();

        // 현재 시각이 아니라 lag만큼 과거를 기준으로 확정한다.
        LocalDateTime threshold = LocalDateTime.now().minusSeconds(watermarkLagSeconds);
        Long safeMaxId = chatMessageRepository
            .findMaxIdByRoomIdAndCreatedBefore(roomId, threshold);

        if (safeMaxId != null && safeMaxId > syncedId) {
            long delta = chatMessageRepository
                .countByChatRoom_IdAndIdGreaterThanAndIdLessThanEqual(
                    roomId, syncedId, safeMaxId);

            cp.advance(safeMaxId, delta);
            log.info("checkpoint 갱신 - roomId={}, syncedId={}->{}, delta={}, cumulative={}, threshold={}",
                roomId, syncedId, safeMaxId, delta, cp.getCumulativeCount(), threshold);
        }

        Long result = cp.getCumulativeCount();
        safeSetCache(roomId, result);
        return result;
    }

    private void safeSetCache(Long roomId, Long value) {
        try {
            chatRoomRedisRepository.setSequenceIfGreater(roomId, value);
        } catch (Exception e) {
            log.debug("Redis 캐시 갱신 실패 (무시) - roomId={}", roomId);
        }
    }
}