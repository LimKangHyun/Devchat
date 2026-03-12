package project.backend.domain.chat.chatmessage.dao;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import project.backend.domain.chat.chatmessage.entity.ChatMessage;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByIdIn(List<Long> ids);

    // 채팅방 입장 시, cursor가 null값이므로, 초반 메시지를 가져올 메서드
    List<ChatMessage> findByChatRoom_IdOrderByIdDesc(Long roomId, Pageable pageable);

    // 커서기반 무한스크롤 메서드
    List<ChatMessage> findByChatRoom_IdAndIdLessThanOrderByIdDesc(Long roomId, Long cursor,
        Pageable pageable);

    @Query("""
        SELECT cm FROM ChatMessage cm
        LEFT JOIN ChatMessageSearch cms ON cm.id = cms.id
        WHERE cm.sendAt > :from
        AND cms.id IS NULL
        """)
    List<ChatMessage> findMissingSearchIndex(@Param("from") LocalDateTime from, Pageable pageable);

    Optional<ChatMessage> findTopByChatRoom_IdOrderByIdDesc(Long roomId);

    void deleteByChatRoom_Id(Long chatRoomId);
}
