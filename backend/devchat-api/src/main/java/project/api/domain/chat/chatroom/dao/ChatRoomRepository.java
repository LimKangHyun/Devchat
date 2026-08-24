package project.api.domain.chat.chatroom.dao;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import project.api.domain.chat.chatroom.entity.ChatRoom;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    Optional<ChatRoom> findByInviteCode(String inviteCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({
            @QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000")
    })
    @Query("SELECT r FROM ChatRoom r WHERE r.inviteCode = :inviteCode")
    Optional<ChatRoom> findByInviteCodeWithLock(@Param("inviteCode") String inviteCode);

    @Query("""
        SELECT cr
        FROM ChatRoom cr
        JOIN cr.participants cp
        WHERE cp.participant.id = :ownerId AND cp.isOwner=true AND cp.isActive = true
        """)
    Page<ChatRoom> findAllRoomsByOwnerId(Long ownerId, Pageable pageable);

    @Query("""
        SELECT cr.id AS chatRoomId, cr.name AS name, cr.inviteCode AS inviteCode,
               cp.lastReadSequence AS lastReadSequence, cr.repositoryUrl AS repositoryUrl,
               cr.indexingStatus AS indexingStatus
        FROM ChatRoom cr
        JOIN cr.participants cp
        WHERE cp.participant.id = :memberId AND cp.isActive = true
    """)
    List<ChatRoomWithSequenceProjection> findAllRoomsWithSequenceByParticipantId(
            @Param("memberId") Long memberId);

    boolean existsByRepositoryUrl(String repositoryUrl);
}
