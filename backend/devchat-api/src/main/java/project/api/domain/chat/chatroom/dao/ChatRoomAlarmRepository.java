package project.api.domain.chat.chatroom.dao;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import project.api.domain.chat.chatroom.entity.ChatRoomAlarm;
import project.api.domain.chat.chatroom.entity.ChatRoomAlarm.ChatRoomAlarmId;

public interface ChatRoomAlarmRepository extends JpaRepository<ChatRoomAlarm, ChatRoomAlarmId> {

	@Query("SELECT ca.enabled FROM ChatRoomAlarm ca WHERE ca.id.memberId = :memberId AND ca.id.roomId = :roomId")
	boolean findEnabledByMemberIdAndRoomId(@Param("memberId") Long memberId,
		@Param("roomId") Long roomId);

	Optional<ChatRoomAlarm> findByIdMemberIdAndIdRoomId(Long memberId, Long roomId);

	@Query("SELECT a.id.roomId, a.enabled FROM ChatRoomAlarm a WHERE a.id.memberId = :memberId AND a.id.roomId IN :roomIds")
	List<Object[]> findEnabledList(@Param("memberId") Long memberId,
		@Param("roomIds") List<Long> roomIds);

	// 또는 편의용으로 Map으로 변환하는 default 메서드 제공
	default Map<Long, Boolean> findEnabledMap(Long memberId, List<Long> roomIds) {
		return findEnabledList(memberId, roomIds).stream()
			.collect(Collectors.toMap(
				row -> (Long) row[0],
				row -> (Boolean) row[1]
			));
	}
}
