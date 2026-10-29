package project.api.auth.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import project.api.auth.entity.LoginSession;

import java.time.LocalDateTime;
import java.util.List;

public interface LoginSessionRepository extends JpaRepository<LoginSession, String> {

    List<LoginSession> findByMemberIdAndRevokedAtIsNullAndExpiresAtAfterOrderByLastUsedAtDesc(
            Long memberId, LocalDateTime now);

    @Modifying
    @Query("delete from LoginSession s where s.expiresAt < :before or s.revokedAt < :before")
    int deleteExpiredOrRevokedBefore(@Param("before") LocalDateTime before);
}
