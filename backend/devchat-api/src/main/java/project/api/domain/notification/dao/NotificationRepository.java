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
