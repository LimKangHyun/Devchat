package project.api.domain.chat.chatsearch.dao;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import project.api.domain.chat.chatsearch.entity.ChatMessageSearch;

public interface ChatMessageSearchRepository extends JpaRepository<ChatMessageSearch, Long> {

    @Query(value = """
        SELECT id
        FROM chat_message_search
        WHERE room_id = :roomId
        AND MATCH(content) AGAINST (:keyword IN BOOLEAN MODE)
        AND (:lastMessageId IS NULL OR id < :lastMessageId)
        ORDER BY id DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<Long> searchIdsByKeywordAndRoomIdWithCursor(
        @Param("keyword") String keyword,
        @Param("roomId") Long roomId,
        @Param("lastMessageId") Long lastMessageId,
        @Param("limit") int limit
    );

    @Query(value = """
        SELECT COUNT(*)
        FROM chat_message_search
        WHERE room_id = :roomId
        AND MATCH(content) AGAINST (:keyword IN BOOLEAN MODE)
        """, nativeQuery = true)
    long countByKeywordAndRoomId(@Param("keyword") String keyword, @Param("roomId") Long roomId);

    void deleteByRoomId(Long roomId);
}

