package project.backend.domain.chat.chatroom.app;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import project.backend.domain.chat.chatroom.dao.ChatRoomRedisRepository;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatRoomRedisService {

    private final ChatRoomRedisRepository chatRoomRedisRepository;
    private final ChatRoomSyncService chatRoomSyncService;

    public Long genMessageSeq(Long roomId) {
        Long seq = chatRoomRedisRepository.genMessageSeq(roomId);
        if (seq == -1L) {
            return chatRoomSyncService.recoverFromDb(roomId);
        }
        return seq;
    }

    public List<Long> getSortedRoomIds(List<Long> roomIds) {
        return chatRoomRedisRepository.getSortedRoomIds(roomIds);
    }

    public Set<String> getAndClearUpdatedRooms() {
        return chatRoomRedisRepository.getAndClearUpdatedRooms();
    }

    public void setSequence(Long roomId, Long sequence) {
        chatRoomRedisRepository.setSequence(roomId, sequence);
    }

    public void bulkSetSequences(Map<Long, Long> sequences) {
        chatRoomRedisRepository.bulkSetSequences(sequences);
    }

    public Long getSequence(Long roomId) {
        return chatRoomRedisRepository.getSequence(roomId);
    }

    public List<Long> getSequences(List<Long> roomIds) {
        return chatRoomRedisRepository.getSequences(roomIds);
    }
}