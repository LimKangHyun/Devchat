package project.backend.domain.chat.chatmessage.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import project.backend.domain.chat.chatmessage.entity.ChatMessageIndexStatus;

import java.util.List;

public interface ChatMessageIndexStatusRepository extends JpaRepository<ChatMessageIndexStatus, Long> {

    List<ChatMessageIndexStatus> findTop100ByOrderByMessageIdAsc();

    @Modifying
    @Query("DELETE FROM ChatMessageIndexStatus c WHERE c.messageId IN :ids")
    void deleteAllByMessageIdIn(@Param("ids") List<Long> ids);

}