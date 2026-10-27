package project.api.domain.chat.chatroom.app;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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
        // Redis 캐시는 커밋한 뒤에 갱신한다. 예전에는 행 락(FOR UPDATE)을 쥔 채 Redis를 불러,
        // Redis가 느리면 그동안 같은 방의 재구성이 락을 기다리다 시간 초과(3초)로 실패했다.
        // 커밋된 값만 캐시에 올라가므로, 롤백된 값이 캐시에 남는 일도 없다.
        setCacheAfterCommit(roomId, result);
        return result;
    }

    private void setCacheAfterCommit(Long roomId, Long value) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            safeSetCache(roomId, value);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                safeSetCache(roomId, value);
            }
        });
    }

    private void safeSetCache(Long roomId, Long value) {
        try {
            chatRoomRedisRepository.setSequenceIfGreater(roomId, value);
        } catch (Exception e) {
            log.debug("Redis 캐시 갱신 실패 (무시) - roomId={}", roomId);
        }
    }
}