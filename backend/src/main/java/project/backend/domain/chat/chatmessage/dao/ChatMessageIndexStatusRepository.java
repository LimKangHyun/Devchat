package project.backend.domain.chat.chatmessage.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import project.backend.domain.chat.chatmessage.entity.ChatMessageIndexStatus;

import java.util.List;

public interface ChatMessageIndexStatusRepository extends JpaRepository<ChatMessageIndexStatus, Long> {

    List<ChatMessageIndexStatus> findTop100ByOrderByMessageIdAsc();

}