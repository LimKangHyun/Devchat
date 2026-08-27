package project.api.domain.chat.chatmessage.dao;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import project.api.domain.chat.chatmessage.dto.ChatMessageSearchProjection;
import project.api.domain.chat.chatmessage.entity.ChatMessage;
import project.api.domain.chat.chatmessage.entity.MessageType;

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

    @Query("SELECT MAX(m.id) FROM ChatMessage m WHERE m.chatRoom.id = :roomId")
    Long findMaxIdByChatRoom_Id(@Param("roomId") Long roomId);

    long countByChatRoom_IdAndIdGreaterThan(Long roomId, Long id);

    @Query("SELECT MAX(m.id) FROM ChatMessage m " +
        "WHERE m.chatRoom.id = :roomId AND m.createdAt < :threshold")
    Long findMaxIdByRoomIdAndCreatedBefore(@Param("roomId") Long roomId,
        @Param("threshold") LocalDateTime threshold);

    /**
     * 증분 카운트. 상한(maxId)을 반드시 함께 받는다 —
     * 워터마크가 safeMaxId까지만 전진하는데 카운트가 그 너머까지 세면,
     * 다음 실행에서 같은 구간을 다시 세어 중복 집계된다.
     */
    long countByChatRoom_IdAndIdGreaterThanAndIdLessThanEqual(
        Long roomId, Long exclusiveFrom, Long inclusiveTo);
}
