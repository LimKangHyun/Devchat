package project.backend.domain.chat.chatroom.app;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import project.backend.domain.chat.chatroom.dao.ChatRoomRedisRepository;
import project.backend.domain.chat.chatroom.dao.ChatRoomRepository;
import project.backend.domain.chat.chatroom.entity.ChatRoom;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatRoomSequenceScheduler {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomRedisRepository chatRoomRedisRepository;
    private final ChatRoomService chatRoomService;

    @Scheduled(fixedRate = 60000)
    public void syncLastSequence() {
        try {
            List<ChatRoom> rooms = chatRoomRepository.findAll();
            if (rooms.isEmpty()) return;

            List<Long> roomIds = rooms.stream().map(ChatRoom::getId).toList();
            List<Long> redisSequences = chatRoomRedisRepository.getSequences(roomIds);

            chatRoomService.syncSequencesToDb(rooms, redisSequences);
            log.info("last_sequence 동기화 배치 완료 - 대상 방 개수: {}개", rooms.size());

        } catch (Exception e) {
            log.error("시퀀스 동기화 배치 중 에러 발생: {}", e.getMessage());
        }
    }
}