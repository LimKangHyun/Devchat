package project.backend.domain.chat.chatmessage.dao;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import project.backend.domain.chat.chatmessage.entity.ChatMessage;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

	List<ChatMessage> findByIdIn(List<Long> ids);

	// 채팅방 입장 시, cursor가 null값이므로, 초반 메시지를 가져올 메서드
	List<ChatMessage> findByChatRoom_IdOrderByIdDesc(Long roomId, Pageable pageable);

	// 커서기반 무한스크롤 메서드
	List<ChatMessage> findByChatRoom_IdAndIdLessThanOrderByIdDesc(Long roomId, Long cursor,
		Pageable pageable);

}
