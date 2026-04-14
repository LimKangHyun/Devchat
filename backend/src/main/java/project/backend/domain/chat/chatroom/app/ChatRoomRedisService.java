package project.backend.domain.chat.chatroom.app;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
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
public class ChatRoomRedisService {

    private final ChatRoomRedisRepository chatRoomRedisRepository;
    private final ChatRoomRepository chatRoomRepository;

    public Long handleMessageDelivery(Long roomId) {
        Long seq = chatRoomRedisRepository.handleMessageDelivery(roomId);

        if (seq != null && seq == -1L) {
            ChatRoom findRoom = chatRoomRepository.findById(roomId)
                    .orElseThrow(() -> new ChatRoomException(ChatRoomErrorCode.CHATROOM_NOT_FOUND));

            long dbSeq = findRoom.getLastSequence();

            Long recoveredSeq = chatRoomRedisRepository.recoverAndIncr(roomId, dbSeq);

            log.warn("Redis sequence 복구 - roomId: {}, dbSeq: {}, 복구값: {}", roomId, dbSeq, recoveredSeq);
            return recoveredSeq;
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

    public Long getSequence(Long roomId) {
        return chatRoomRedisRepository.getSequence(roomId);
    }

    public List<Long> getSequences(List<Long> roomIds) {
        return chatRoomRedisRepository.getSequences(roomIds);
    }

}