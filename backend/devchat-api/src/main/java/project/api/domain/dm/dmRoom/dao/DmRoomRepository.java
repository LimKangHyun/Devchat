package project.api.domain.dm.dmRoom.dao;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import project.api.domain.dm.dmRoom.entity.DmRoom;

public interface DmRoomRepository extends JpaRepository<DmRoom, Long> {

	@Query("""
			SELECT r 
				FROM DmRoom r 
			WHERE r.member1.id = :minId AND r.member2.id = :maxId
		""")
	Optional<DmRoom> findByMembers(@Param("minId") Long minId, @Param("maxId") Long maxId);

	/** DM 대화 당사자인지 (웹소켓 구독 권한 확인용) */
	@Query("""
			SELECT COUNT(r) > 0
				FROM DmRoom r
			WHERE r.id = :roomId AND (r.member1.id = :memberId OR r.member2.id = :memberId)
		""")
	boolean isMember(@Param("roomId") Long roomId, @Param("memberId") Long memberId);
}
