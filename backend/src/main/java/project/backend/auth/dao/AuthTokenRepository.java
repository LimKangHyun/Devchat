package project.backend.auth.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import project.backend.auth.entity.AuthToken;

public interface AuthTokenRepository extends JpaRepository<AuthToken, Long> {
}
