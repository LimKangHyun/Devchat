package project.backend.domain.chat.chatroom.dao;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import project.backend.domain.chat.chatroom.entity.ChatParticipant;
import project.backend.domain.chat.chatroom.entity.ChatRoom;

public interface ChatParticipantRepository extends JpaRepository<ChatParticipant, Long> {

	boolean existsByParticipantIdAndChatRoomId(Long participantId, Long chatRoomId);

	@EntityGraph(attributePaths = {"participant"})
	List<ChatParticipant> findByChatRoom(ChatRoom chatRoom);

	Optional<ChatParticipant> findByChatRoomIdAndParticipantId(Long chatRoomId, Long participantId);

	Optional<ChatParticipant> findByChatRoom_IdAndParticipant_Id(Long ChatRoomId,
		Long participantId);
}

