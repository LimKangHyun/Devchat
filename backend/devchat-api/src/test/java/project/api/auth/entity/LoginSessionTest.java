package project.api.auth.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class LoginSessionTest {

    private final LocalDateTime now = LocalDateTime.of(2026, 10, 28, 12, 0);

    @Test
    void rotate_updatesHashAndLastUsed() {
        LoginSession session = LoginSession.create(1L, "old-hash", "Chrome", now, now.plusDays(14));
        LocalDateTime later = now.plusDays(1);
        LocalDateTime newExpires = later.plusDays(14);

        session.rotate("new-hash", later, newExpires);

        assertThat(session.getRefreshTokenHash()).isEqualTo("new-hash");
        assertThat(session.getLastUsedAt()).isEqualTo(later);
        assertThat(session.getExpiresAt()).isEqualTo(newExpires);
    }

    @Test
    void revoke_makesInactive() {
        LoginSession session = LoginSession.create(1L, "hash", "Chrome", now, now.plusDays(14));

        assertThat(session.isActive(now)).isTrue();
        session.revoke(now);

        assertThat(session.isActive(now)).isFalse();
        assertThat(session.getRevokedAt()).isEqualTo(now);
    }

    @Test
    void expired_isInactive() {
        LoginSession session = LoginSession.create(1L, "hash", "Chrome", now.minusDays(14), now.minusSeconds(1));

        assertThat(session.isActive(now)).isFalse();
    }
}
