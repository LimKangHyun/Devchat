package project.backend.domain.chat.chatmessage.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import project.backend.domain.chat.chatmessage.dto.ChatMessageSearchProjection;
import project.backend.domain.chat.chatmessage.entity.ChatMessage;
import project.backend.domain.chat.chatmessage.entity.MessageType;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByIdIn(List<Long> ids);

    // 채팅방 입장 시, cursor가 null값이므로, 초반 메시지를 가져올 메서드
    @EntityGraph(attributePaths = {"aiReview"})
    List<ChatMessage> findByChatRoom_IdOrderByIdDesc(Long roomId, Pageable pageable);

    // 커서기반 무한스크롤 메서드
    @EntityGraph(attributePaths = {"aiReview"})
    List<ChatMessage> findByChatRoom_IdAndIdLessThanOrderByIdDesc(Long roomId, Long cursor,
        Pageable pageable);

    void deleteByChatRoom_Id(Long chatRoomId);

    @Query("SELECT m.id as id, m.content as content, m.chatRoom.id as chatRoomId FROM ChatMessage m WHERE m.id IN :ids")
    List<ChatMessageSearchProjection> findSearchDataByIdIn(@Param("ids") List<Long> ids);

    @Query("""
        SELECT m.id as id, m.content as content, m.chatRoom.id as chatRoomId
        FROM ChatMessage m
        JOIN ChatMessageIndexStatus s ON m.id = s.messageId
        ORDER BY s.messageId ASC
        LIMIT 100
    """)
    List<ChatMessageSearchProjection> findTop100WithIndexStatus();

    Optional<ChatMessage> findByChatRoom_IdAndPrNumberAndType(Long roomId, Integer prNumber, MessageType type);
}
