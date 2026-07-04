package project.api.domain.chat.chatroom.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import project.api.domain.chat.chatroom.entity.FallbackSequenceRecovery;

public interface FallbackSequenceRecoveryRepository extends JpaRepository<FallbackSequenceRecovery, Long> {

    @Modifying
    @Query(value = "INSERT IGNORE INTO fallback_sequence_recovery (room_id, created_at) VALUES (:roomId, NOW())",
            nativeQuery = true)
    void insertIgnore(@Param("roomId") Long roomId);

}