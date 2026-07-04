package project.api.domain.dm.dmMessage.dao;


import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import project.api.domain.dm.dmMessage.dto.DmMessageResponse;
import project.api.domain.dm.dmMessage.entity.DmMessage;

public interface DmMessageRepository extends JpaRepository<DmMessage, Long> {

	@Query("SELECT new project.api.domain.dm.dmMessage.dto.DmMessageResponse(" +
		"m.room.id, m.sender.id, m.content, m.sender.nickname, m.type, m.id, m.sentAt) " +
		"FROM DmMessage m " +
		"WHERE m.room.id = :roomId " +
		"ORDER BY m.sentAt DESC")
	Page<DmMessageResponse> findMessagesByRoomId(@Param("roomId") Long roomId, Pageable pageable);
}
