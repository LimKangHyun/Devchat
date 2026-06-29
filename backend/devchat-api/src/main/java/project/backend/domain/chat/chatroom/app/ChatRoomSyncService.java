package project.backend.domain.chat.chatroom.app;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.backend.domain.chat.chatroom.dao.ChatRoomRedisRepository;
import project.backend.domain.chat.chatroom.dao.ChatRoomRepository;
import project.backend.domain.chat.chatroom.dao.FallbackSequenceRecoveryRepository;
import project.backend.domain.chat.chatroom.entity.ChatRoom;
import project.backend.domain.chat.chatroom.entity.FallbackSequenceRecovery;
import project.backend.global.exception.errorcode.ChatRoomErrorCode;
import project.backend.global.exception.ex.ChatRoomException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatRoomSyncService {

    private final FallbackSequenceRecoveryRepository fallbackSequenceRecoveryRepository;
    private final ChatRoomRedisRepository chatRoomRedisRepository;
    private final ChatRoomRepository chatRoomRepository;

    public Long getOrRecoverSeq(Long roomId) {
        Long seq = chatRoomRedisRepository.genMessageSeq(roomId);
        if (seq == -1L) {
            return recoverFromDb(roomId);
        }
        return seq;
    }

    public Long recoverFromDb(Long roomId) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new ChatRoomException(ChatRoomErrorCode.CHATROOM_NOT_FOUND));

        long dbSeq = room.getLastSequence();
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
        fallbackSequenceRecoveryRepository.insertIgnore(roomId);
        return chatRoomRepository.findLastInsertId();
    }

    @Transactional
    public void recoverSequences() {
        List<Long> roomIds = findFallbackRoomIds();
        if (roomIds.isEmpty()) return;

        log.info("Redis 복구 시작 - 대상 채팅방 수: {}", roomIds.size());

        Map<Long, Long> dbSeqMap = findDbSequences(roomIds);
        List<Long> redisSeqs = chatRoomRedisRepository.getSequences(roomIds);
        Map<Long, Long> maxSeqMap = buildMaxSeqMap(roomIds, dbSeqMap, redisSeqs);

        chatRoomRedisRepository.bulkSetSequences(maxSeqMap);
        fallbackSequenceRecoveryRepository.deleteAll();

        log.info("Redis 복구 완료 - roomIds: {}", roomIds);
    }

    private List<Long> findFallbackRoomIds() {
        return fallbackSequenceRecoveryRepository.findAll()
                .stream()
                .map(FallbackSequenceRecovery::getRoomId)
                .toList();
    }

    private Map<Long, Long> findDbSequences(List<Long> roomIds) {
        return chatRoomRepository.findAllById(roomIds)
                .stream()
                .collect(Collectors.toMap(ChatRoom::getId, ChatRoom::getLastSequence));
    }

    private Map<Long, Long> buildMaxSeqMap(List<Long> roomIds,
                                           Map<Long, Long> dbSeqMap,
                                           List<Long> redisSeqs) {
        Map<Long, Long> maxSeqMap = new HashMap<>();
        for (int i = 0; i < roomIds.size(); i++) {
            Long roomId = roomIds.get(i);
            long dbSeq = dbSeqMap.getOrDefault(roomId, 0L);
            long redisSeq = redisSeqs.get(i) != null ? redisSeqs.get(i) : 0L;
            maxSeqMap.put(roomId, Math.max(dbSeq, redisSeq));
        }
        return maxSeqMap;
    }
}