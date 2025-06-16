package project.backend.domain.member.notification.dao;

import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import project.backend.domain.member.notification.dto.AlertTemplate;
import project.backend.domain.member.notification.entity.Notification;

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
}
