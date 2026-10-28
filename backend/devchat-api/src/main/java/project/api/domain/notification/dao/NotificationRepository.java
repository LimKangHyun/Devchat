package project.api.domain.notification.dao;

import io.lettuce.core.dynamic.annotation.Param;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import project.api.domain.member.entity.Member;
import project.api.domain.notification.entity.Notification;
import project.api.domain.notification.entity.NotificationType;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

	/** 그 방에서 온 내 멘션 알림을 읽음 처리한다 (방을 열어 메시지를 봤을 때) */
	@org.springframework.data.jpa.repository.Modifying(clearAutomatically = true)
	@Query("""
			UPDATE Notification n SET n.isRead = true
			WHERE n.receiver.id = :receiverId
			  AND n.type = :type
			  AND n.referenceId = :roomId
			  AND n.isRead = false
		""")
	int markReadByRoom(@org.springframework.data.repository.query.Param("receiverId") Long receiverId,
		@org.springframework.data.repository.query.Param("roomId") Long roomId,
		@org.springframework.data.repository.query.Param("type") NotificationType type);

	/** 오래된 알림 정리. 아직 처리하지 않은 알림(keepUnreadType, 예: 친구 요청)은 남긴다 */
	@org.springframework.data.jpa.repository.Modifying
	@Query("""
			DELETE FROM Notification n
			WHERE n.createdAt < :before
			  AND NOT (n.type = :keepUnreadType AND n.isRead = false)
		""")
	int deleteOlderThan(@org.springframework.data.repository.query.Param("before") java.time.LocalDateTime before,
		@org.springframework.data.repository.query.Param("keepUnreadType") NotificationType keepUnreadType);

	@Query("""
			SELECT n
			FROM Notification n
			WHERE n.receiver.id = :receiverId
		""")
	Page<Notification> getNotifications(@Param("receiverId") Long receiverId,
		Pageable pageable);

	@Query("""
			SELECT n
			FROM Notification n
			WHERE n.receiver.id = :receiverId
			AND n.isRead = false
		""")
	Page<Notification> getNotReadNotification(@Param("receiverId") Long receiverId,
		Pageable pageable);

	// 수정 or 거절시 읽음 처리 되므로 거절시 읽지 않은 친구요청은 하나만 존재할 수 있다.
	@Query("""
			SELECT n FROM Notification n
			WHERE n.receiver = :receiver
			  AND n.sender = :sender
			  AND n.type = :type
			  AND n.isRead = false
		""")
	Optional<Notification> getNotificationByType(
		@Param("receiver") Member receiver,
		@Param("sender") Member sender,
		@Param("type") NotificationType type
	);


}
