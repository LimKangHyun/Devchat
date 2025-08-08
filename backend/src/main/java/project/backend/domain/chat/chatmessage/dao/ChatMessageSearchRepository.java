package project.backend.domain.chat.chatmessage.dao;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import project.backend.domain.chat.chatmessage.entity.ChatMessageSearch;

public interface ChatMessageSearchRepository extends JpaRepository<ChatMessageSearch, Long> {

    @Query(value = """
        SELECT id FROM chat_message_search
        WHERE room_id = :roomId
        AND MATCH(content) AGAINST (:keyword IN BOOLEAN MODE)
        LIMIT :limit OFFSET :offset
        """, nativeQuery = true)
        // 페이지 하나를 어떻게 구성할것인지 limit: 한 페이지에 담을 결과 개수, offset: 시작 위치
    List<Long> searchIdsByKeywordAndRoomId(
        @Param("keyword") String keyword,
        @Param("roomId") Long roomId,
        @Param("limit") int limit,
        @Param("offset") int offset
    );

    @Query(value = """
        SELECT COUNT(*)
        FROM chat_message_search
        WHERE room_id = :roomId
        AND MATCH(content) AGAINST (:keyword IN BOOLEAN MODE)
        """, nativeQuery = true)
    long countByKeywordAndRoomId(@Param("keyword") String keyword, @Param("roomId") Long roomId);
}

