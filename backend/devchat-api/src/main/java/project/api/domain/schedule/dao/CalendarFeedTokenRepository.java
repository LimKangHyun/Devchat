package project.api.domain.schedule.dao;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import project.api.domain.schedule.entity.CalendarFeedToken;

public interface CalendarFeedTokenRepository extends JpaRepository<CalendarFeedToken, Long> {

    Optional<CalendarFeedToken> findByTokenHash(String tokenHash);
}
