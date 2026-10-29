package project.api.domain.chat.chatroom.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import project.api.domain.chat.chatroom.entity.ChatParticipant;

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

    /** 새 메시지 휴대폰 알림 대상: 보낸 사람을 뺀 참여자 중 이 방 알림을 끄지 않은 사람 */
    @Query("""
        SELECT cp.participant.id
        FROM ChatParticipant cp
        WHERE cp.chatRoom.id = :roomId
          AND cp.isActive = true
          AND cp.participant.id <> :senderId
          AND NOT EXISTS (
              SELECT 1 FROM ChatRoomAlarm a
              WHERE a.id.memberId = cp.participant.id AND a.id.roomId = :roomId AND a.enabled = false
          )
    """)
    List<Long> findMessagePushTargets(@org.springframework.data.repository.query.Param("roomId") Long roomId,
                                      @org.springframework.data.repository.query.Param("senderId") Long senderId);

    Optional<ChatParticipant> findByChatRoomIdAndParticipantId(Long chatRoomId, Long participantId);

    Optional<ChatParticipant> findTopByParticipantIdAndIsActiveTrueOrderByJoinAtDesc(
        Long participantId);

    Optional<ChatParticipant> findByChatRoomIdAndIsOwnerTrue(Long roomId);

    boolean existsByParticipantIdAndChatRoomIdAndIsActiveTrue(Long participantId, Long chatRoomId);

    /** 두 사람이 같은 방에 둘 다 활성 참여 중인지 (일정 참여자 검사) */
    @Query("""
        SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END
        FROM ChatParticipant a, ChatParticipant b
        WHERE a.chatRoom.id = b.chatRoom.id
          AND a.participant.id = :a
          AND b.participant.id = :b
          AND a.isActive = true
          AND b.isActive = true
    """)
    boolean existsSharedActiveRoom(@org.springframework.data.repository.query.Param("a") Long a,
                                   @org.springframework.data.repository.query.Param("b") Long b);

    void deleteByChatRoom_Id(Long chatRoomId);

    int countByParticipantIdAndIsActiveTrue(Long participantId);

    int countByChatRoomIdAndIsActiveTrue(Long chatRoomId);
}