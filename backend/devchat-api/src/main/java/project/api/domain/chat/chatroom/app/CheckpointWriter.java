package project.api.domain.chat.chatroom.app;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    @Transactional
    public Long reconstruct(Long roomId) {
        ChatRoomCheckpoint cp = checkpointRepository.findByRoomIdForUpdate(roomId)
                .orElseGet(() -> checkpointRepository.save(new ChatRoomCheckpoint(roomId)));

        Long syncedId = cp.getSyncedMessageId();
        Long newMaxId = chatMessageRepository.findMaxIdByChatRoom_Id(roomId);

        if (newMaxId != null && newMaxId > syncedId) {
            long delta = chatMessageRepository.countByChatRoom_IdAndIdGreaterThan(roomId, syncedId);
            cp.advance(newMaxId, delta);
            log.info("checkpoint 갱신 - roomId={}, syncedId={}->{}, delta={}, cumulative={}",
                    roomId, syncedId, newMaxId, delta, cp.getCumulativeCount());
        }

        Long result = cp.getCumulativeCount();
        safeSetCache(roomId, result);
        return result;
    }

    private void safeSetCache(Long roomId, Long value) {
        try {
            chatRoomRedisRepository.setSequence(roomId, value);
        } catch (Exception e) {
            log.debug("Redis 캐시 갱신 실패 (무시) - roomId={}", roomId);
        }
    }
}