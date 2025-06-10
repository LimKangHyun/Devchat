package project.backend.domain.chat.chatroom.dao;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import project.backend.domain.chat.chatroom.entity.ChatParticipant;
import project.backend.domain.chat.chatroom.entity.ChatRoom;

public interface ChatParticipantRepository extends JpaRepository<ChatParticipant, Long> {

	@Query("""
    SELECT CASE WHEN COUNT(cp) > 0 THEN true ELSE false END 
    FROM ChatParticipant cp 
    WHERE cp.participant.id = :participantId 
      AND cp.chatRoom.id = :chatRoomId 
      AND cp.isActive = true
    """)
	boolean existsByParticipantIdAndChatRoomId(@Param("participantId") Long participantId,
		@Param("chatRoomId") Long chatRoomId);

	@EntityGraph(attributePaths = {"participant"})
	@Query("""
    SELECT cp 
    FROM ChatParticipant cp 
    WHERE cp.chatRoom = :chatRoom 
      AND cp.isActive = true
    """)
	List<ChatParticipant> findByChatRoom(@Param("chatRoom") ChatRoom chatRoom);

	@Query("""
    SELECT cp 
    FROM ChatParticipant cp 
    WHERE cp.chatRoom.id = :chatRoomId 
      AND cp.participant.id = :participantId 
      AND cp.isActive = true
    """)
	Optional<ChatParticipant> findByChatRoomIdAndParticipantId(@Param("chatRoomId") Long chatRoomId,
		@Param("participantId") Long participantId);

	// 재입장을 위해 활성/비활성 관계없이 찾는 메서드 추가
	@Query("""
    SELECT cp 
    FROM ChatParticipant cp 
    WHERE cp.chatRoom.id = :chatRoomId 
      AND cp.participant.id = :participantId
    """)
	Optional<ChatParticipant> findByChatRoomIdAndParticipantIdIgnoreActive(
		@Param("chatRoomId") Long chatRoomId, @Param("participantId") Long participantId);

	long countByParticipantIdAndIsActiveTrue(Long participantId);

	// 가장 최근에 생성된(ID가 큰 = 나중에 생성 되었음)
	Optional<ChatParticipant> findTopByParticipantIdAndIsActiveTrueOrderByJoinAtDesc(
		Long participantId);

	@Query("""
    SELECT cp 
    FROM ChatParticipant cp 
    WHERE cp.chatRoom.id = :roomId 
      AND cp.isOwner = true 
      AND cp.isActive = true
    """)
	Optional<ChatParticipant> findByChatRoomIdAndIsOwnerTrue(@Param("roomId") Long roomId);
}

