package project.api.auth.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import project.api.auth.entity.AuthToken;

public interface AuthTokenRepository extends JpaRepository<AuthToken, Long> {
}
