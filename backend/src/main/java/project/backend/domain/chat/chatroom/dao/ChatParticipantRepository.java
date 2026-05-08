package project.backend.domain.chat.chatroom.dao;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import project.backend.domain.chat.chatroom.entity.ChatParticipant;

public interface ChatParticipantRepository extends JpaRepository<ChatParticipant, Long> {

    @EntityGraph(attributePaths = {"participant"})
    @Query("""
        SELECT cp 
        FROM ChatParticipant cp 
        WHERE cp.chatRoom.id = :chatRoomId 
          AND cp.isActive = true
    """)
    List<ChatParticipant> findByChatRoomId(Long chatRoomId);

    Optional<ChatParticipant> findByChatRoomIdAndParticipantIdAndIsActiveTrue(Long chatRoomId,
        Long participantId);

    Optional<ChatParticipant> findByChatRoomIdAndParticipantId(Long chatRoomId, Long participantId);

    Optional<ChatParticipant> findTopByParticipantIdAndIsActiveTrueOrderByJoinAtDesc(
        Long participantId);

    Optional<ChatParticipant> findByChatRoomIdAndIsOwnerTrue(Long roomId);

    boolean existsByParticipantIdAndChatRoomIdAndIsActiveTrue(Long participantId, Long chatRoomId);

    void deleteByChatRoom_Id(Long chatRoomId);

    int countByParticipantIdAndIsActiveTrue(Long participantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT COUNT(cp) FROM ChatParticipant cp WHERE cp.chatRoom.id = :chatRoomId AND cp.isActive = true")
    int countByChatRoomIdAndIsActiveTrueWithLock(@Param("chatRoomId") Long chatRoomId);
}

