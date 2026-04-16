package project.backend.domain.chat.chatroom.app;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.backend.domain.chat.chatroom.dao.ChatParticipantRepository;
import project.backend.domain.chat.chatroom.dao.ChatRoomRepository;
import project.backend.domain.chat.chatroom.dao.ChatRoomWithSequenceProjection;
import project.backend.domain.chat.chatroom.dto.AllRoomsResponse;
import project.backend.domain.chat.chatroom.entity.ChatRoom;

import java.util.*;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ChatRoomSequenceService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatParticipantRepository chatParticipantRepository;
    private final ChatRoomCacheService chatRoomCacheService;
    private final ChatRoomRedisService chatRoomRedisService;

    public List<AllRoomsResponse> findAllRoomsWithUnread(Long memberId,
                                                         List<ChatRoomWithSequenceProjection> roomProjections,
                                                         Map<Long, Boolean> alarmEnabledMap) {

        List<Long> roomIds = new ArrayList<>(roomProjections.size());
        Map<Long, ChatRoomWithSequenceProjection> projectionMap = new HashMap<>();
        Map<Long, Integer> sequenceIndexMap = new HashMap<>();

        for (int i = 0; i < roomProjections.size(); i++) {
            ChatRoomWithSequenceProjection p = roomProjections.get(i);
            Long id = p.getChatRoomId();
            roomIds.add(id);
            projectionMap.put(id, p);
            sequenceIndexMap.put(id, i);
        }

        List<Long> sortedRoomIds = chatRoomCacheService.getSortedRoomIds(roomIds);
        List<Long> sequences = chatRoomCacheService.getSequences(roomIds);

        return sortedRoomIds.stream().map(roomId -> {
            ChatRoomWithSequenceProjection p = projectionMap.get(roomId);
            boolean alarmEnabled = alarmEnabledMap.getOrDefault(roomId, true);
            Long lastRead = (p.getLastReadSequence() != null) ? p.getLastReadSequence() : 0L;

            Long unreadCount = null;
            Integer originalIdx = sequenceIndexMap.get(roomId);
            if (sequences != null && originalIdx != null) {
                unreadCount = calculateUnread(lastRead, sequences.get(originalIdx));
            }

            return new AllRoomsResponse(
                    roomId,
                    p.getInviteCode(),
                    p.getName(),
                    alarmEnabled,
                    unreadCount
            );
        }).toList();
    }

    @Transactional
    public void updateLastReadSequence(Long roomId, Long memberId) {
        Long currentSequence = chatRoomCacheService.getLatestSequence(roomId);
        chatParticipantRepository
                .findByChatRoomIdAndParticipantIdAndIsActiveTrue(roomId, memberId)
                .ifPresent(p -> p.updateLastReadSequence(currentSequence));
    }

    public Long getLatestSequence(Long roomId) {
        return chatRoomCacheService.getLatestSequence(roomId);
    }

    @Transactional
    public void syncSequencesToDb() {
        Set<String> updatedRoomIds = chatRoomRedisService.getAndClearUpdatedRooms();
        if (updatedRoomIds.isEmpty()) return;

        List<Long> roomIds = updatedRoomIds.stream().map(Long::valueOf).toList();
        List<ChatRoom> rooms = chatRoomRepository.findAllById(roomIds);
        if (rooms.isEmpty()) return;

        List<Long> targetIds = rooms.stream().map(ChatRoom::getId).toList();
        List<Long> redisSequences = chatRoomRedisService.getSequences(targetIds);

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

    private long calculateUnread(long lastReadSequence, long lastMessageSequence) {
        return Math.max(0, lastMessageSequence - lastReadSequence);
    }

    @Transactional
    public Long incrementSequenceFromDb(Long roomId) {
        chatRoomRepository.incrementSequence(roomId);
        return chatRoomRepository.findLastInsertId();
    }
}