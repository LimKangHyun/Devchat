package project.backend.domain.chat.chatroom.app;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.backend.domain.chat.chatroom.dao.ChatParticipantRepository;
import project.backend.domain.chat.chatroom.dao.ChatRoomWithSequenceProjection;
import project.backend.domain.chat.chatroom.dto.AllRoomsResponse;

import java.util.*;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ChatRoomReadService {

    private final ChatParticipantRepository chatParticipantRepository;
    private final ChatRoomSequenceService chatRoomSequenceService;

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

        List<Long> sortedRoomIds = chatRoomSequenceService.getSortedRoomIds(roomIds);
        List<Long> sequences = chatRoomSequenceService.getSequences(roomIds);

        return sortedRoomIds.stream().map(roomId -> {
            ChatRoomWithSequenceProjection p = projectionMap.get(roomId);
            boolean alarmEnabled = alarmEnabledMap.getOrDefault(roomId, true);
            long lastRead = p.getLastReadSequence() != null ? p.getLastReadSequence() : 0L;

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
        Long currentSequence = chatRoomSequenceService.getLatestSequence(roomId);
        chatParticipantRepository
                .findByChatRoomIdAndParticipantIdAndIsActiveTrue(roomId, memberId)
                .ifPresent(p -> p.updateLastReadSequence(currentSequence));
    }

    public Long getLatestSequence(Long roomId) {
        return chatRoomSequenceService.getLatestSequence(roomId);
    }

    private long calculateUnread(long lastReadSequence, long lastMessageSequence) {
        return Math.max(0, lastMessageSequence - lastReadSequence);
    }
}