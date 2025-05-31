package project.backend.domain.chat.chatmessage.dao;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import project.backend.domain.chat.chatmessage.entity.ChatMessage;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

	@Query(value = """
		select m.room_id 
		from chat_message m 
		join chat_participant cp on cp.room_id = m.room_id 
		join member mb on cp.member_id = mb.member_id 
		where mb.email = :email 
		order by m.send_at desc 
		limit 1
		""", nativeQuery = true)
	Optional<Long> findMostRecentRoomIdByMemberEmail(@Param("email") String email);

	List<ChatMessage> findByChatRoom_IdOrderBySendAtAsc(Long roomId);

	List<ChatMessage> findByIdIn(List<Long> ids);
}
