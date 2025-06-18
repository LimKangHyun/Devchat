package project.backend.domain.member.dmRoom.dao;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import project.backend.domain.member.dmRoom.entity.DmRoom;

public interface DmRoomRepository extends JpaRepository<DmRoom, Long> {

	@Query("""
			SELECT r 
				FROM DmRoom r 
			WHERE r.member1.id = :minId AND r.member2.id = :maxId
		""")
	Optional<DmRoom> findByMembers(@Param("minId") Long minId, @Param("maxId") Long maxId);
}
