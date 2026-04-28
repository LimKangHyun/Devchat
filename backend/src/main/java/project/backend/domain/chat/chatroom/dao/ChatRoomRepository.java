package project.backend.domain.chat.chatroom.dao;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import project.backend.domain.chat.chatroom.entity.ChatRoom;


public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    @Query("""
        SELECT DISTINCT cr
        FROM ChatRoom cr
        JOIN cr.participants cp
        WHERE cp.participant.id = :memberId AND cp.isActive = true
        """)
    Page<ChatRoom> findChatRoomsByParticipantId(@Param("memberId") Long memberId,
        Pageable pageable);

    Optional<ChatRoom> findByInviteCode(String inviteCode);

    @Query("""
        SELECT cr
        FROM ChatRoom cr
        JOIN cr.participants cp
        WHERE cp.participant.id = :ownerId AND cp.isOwner=true AND cp.isActive = true
        """)
    Page<ChatRoom> findAllRoomsByOwnerId(Long ownerId, Pageable pageable);

    @Query("""
        SELECT cr.id AS chatRoomId, cr.name AS name, cr.inviteCode AS inviteCode,
        	   cp.lastReadSequence AS lastReadSequence
        FROM ChatRoom cr
        JOIN cr.participants cp
        WHERE cp.participant.id = :memberId AND cp.isActive = true
        """)
    List<ChatRoomWithSequenceProjection> findAllRoomsWithSequenceByParticipantId(
        @Param("memberId") Long memberId);

    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE chat_room SET last_sequence = LAST_INSERT_ID(last_sequence + 1) WHERE room_id = :roomId", nativeQuery = true)
    void incrementSequence(@Param("roomId") Long roomId);

    @Query(value = "SELECT LAST_INSERT_ID()", nativeQuery = true)
    Long findLastInsertId();
}

