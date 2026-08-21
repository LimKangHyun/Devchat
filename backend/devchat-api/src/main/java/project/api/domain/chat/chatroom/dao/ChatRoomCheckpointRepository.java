package project.api.domain.chat.chatroom.dao;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import project.api.domain.chat.chatroom.entity.ChatRoomCheckpoint;

import java.util.List;
import java.util.Optional;

public interface ChatRoomCheckpointRepository extends JpaRepository<ChatRoomCheckpoint, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000")})
    @Query("SELECT c FROM ChatRoomCheckpoint c WHERE c.roomId = :roomId")
    Optional<ChatRoomCheckpoint> findByRoomIdForUpdate(@Param("roomId") Long roomId);

    List<ChatRoomCheckpoint> findByRoomIdIn(List<Long> roomIds);

    void deleteByRoomId(Long roomId);

    @Query("""
    SELECT c.roomId FROM ChatRoomCheckpoint c
    WHERE EXISTS (
        SELECT 1 FROM ChatMessage m
        WHERE m.chatRoom.id = c.roomId AND m.id > c.syncedMessageId
    )
    """)
    List<Long> findStaleRoomIds();
}