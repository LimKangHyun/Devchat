package project.backend.domain.chat.chatroom.dao;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import project.backend.domain.chat.chatroom.entity.ChatParticipant;
import project.backend.domain.chat.chatroom.entity.ChatRoom;

public interface ChatParticipantRepository extends JpaRepository<ChatParticipant, Long> {

	@EntityGraph(attributePaths = {"participant"})
	@Query("""
    SELECT cp 
    FROM ChatParticipant cp 
    WHERE cp.chatRoom = :chatRoom 
      AND cp.isActive = true
    """)
	List<ChatParticipant> findByChatRoom(@Param("chatRoom") ChatRoom chatRoom);

	Optional<ChatParticipant> findByChatRoomIdAndParticipantIdAndIsActiveTrue(Long chatRoomId,
		Long participantId);

	Optional<ChatParticipant> findByChatRoomIdAndParticipantId(Long chatRoomId, Long participantId);

	Optional<ChatParticipant> findTopByParticipantIdAndIsActiveTrueOrderByJoinAtDesc(
		Long participantId);

	Optional<ChatParticipant> findByChatRoomIdAndIsOwnerTrue(Long roomId);

	boolean existsByParticipantIdAndChatRoomIdAndIsActiveTrue(Long participantId, Long chatRoomId);
}

