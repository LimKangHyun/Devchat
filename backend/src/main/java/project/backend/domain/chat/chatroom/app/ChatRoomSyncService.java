package project.backend.domain.chat.chatroom.app;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.backend.domain.chat.chatroom.dao.ChatRoomRedisRepository;
import project.backend.domain.chat.chatroom.dao.ChatRoomRepository;
import project.backend.domain.chat.chatroom.entity.ChatRoom;
import project.backend.global.exception.errorcode.ChatRoomErrorCode;
import project.backend.global.exception.ex.ChatRoomException;

import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatRoomSyncService {

    private final ChatRoomRedisRepository chatRoomRedisRepository;
    private final ChatRoomRepository chatRoomRepository;

    public Long recoverFromDb(Long roomId) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new ChatRoomException(ChatRoomErrorCode.CHATROOM_NOT_FOUND));
        long dbSeq = room.getLastSequence() != null ? room.getLastSequence() : 0L;
        Long recoveredSeq = chatRoomRedisRepository.recoverAndIncr(roomId, dbSeq);
        log.warn("Redis sequence 복구 - roomId: {}, dbSeq: {}, 복구값: {}", roomId, dbSeq, recoveredSeq);
        return recoveredSeq;
    }

    @Transactional
    public void syncToDb() {
        Set<String> updatedRoomIds = chatRoomRedisRepository.getAndClearUpdatedRooms();
        if (updatedRoomIds.isEmpty()) return;

        List<Long> roomIds = updatedRoomIds.stream().map(Long::valueOf).toList();
        List<ChatRoom> rooms = chatRoomRepository.findAllById(roomIds);
        if (rooms.isEmpty()) return;

        List<Long> targetIds = rooms.stream().map(ChatRoom::getId).toList();
        List<Long> redisSequences = chatRoomRedisRepository.getSequences(targetIds);

        for (int i = 0; i < rooms.size(); i++) {
            Long redisSeq = redisSequences.get(i);
            long currentSeq = rooms.get(i).getLastSequence() != null
                    ? rooms.get(i).getLastSequence() : 0L;
            if (redisSeq != null && redisSeq > currentSeq) {
                rooms.get(i).updateLastSequence(redisSeq);
            }
        }
        log.info("last_sequence 동기화 완료 - {}개 채팅방", rooms.size());
    }

    @Transactional
    public Long incrementSequenceFromDb(Long roomId) {
        chatRoomRepository.incrementSequence(roomId);
        return chatRoomRepository.findLastInsertId();
    }
}