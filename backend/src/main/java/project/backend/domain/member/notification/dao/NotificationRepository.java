package project.backend.domain.member.notification.dao;

import io.lettuce.core.dynamic.annotation.Param;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import project.backend.domain.member.entity.Member;
import project.backend.domain.member.notification.dto.AlertTemplate;
import project.backend.domain.member.notification.entity.Notification;
import project.backend.domain.member.notification.entity.NotificationType;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

	@Query("""
			SELECT new project.backend.domain.member.notification.dto.AlertTemplate(
				n.type,
				s.username,
				s.profileImage,
				n.referenceId
			)
			FROM Notification n
			JOIN n.sender s
			WHERE n.receiver.id = :receiverId AND n.isRead = false
			ORDER BY n.createdAt DESC
		""")
	Page<AlertTemplate> getNotificationsAndReadNot(@Param("receiverId") Long receiverId,
		Pageable pageable);


	@Query("""
			SELECT n FROM Notification n
			WHERE n.receiver = :receiver
			  AND n.sender = :sender
			  AND n.type = :type
		""")
	Optional<Notification> getNotificationByType(
		@Param("receiver") Member receiver,
		@Param("sender") Member sender,
		@Param("type") NotificationType type
	);

}
