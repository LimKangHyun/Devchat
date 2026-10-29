package project.api.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

// 기기별 로그인 세션
@Entity
@Table(name = "login_session")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LoginSession {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false, length = 64, columnDefinition = "char(64)")
    private String refreshTokenHash;

    @Column(nullable = false, length = 100)
    private String deviceName;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime lastUsedAt;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    private LocalDateTime revokedAt;

    public static LoginSession create(Long memberId, String refreshTokenHash, String deviceName,
                                      LocalDateTime now, LocalDateTime expiresAt) {
        LoginSession session = new LoginSession();
        session.id = UUID.randomUUID().toString();
        session.memberId = memberId;
        session.refreshTokenHash = refreshTokenHash;
        session.deviceName = deviceName;
        session.createdAt = now;
        session.lastUsedAt = now;
        session.expiresAt = expiresAt;
        return session;
    }

    public void rotate(String newRefreshTokenHash, LocalDateTime now, LocalDateTime newExpiresAt) {
        this.refreshTokenHash = newRefreshTokenHash;
        this.lastUsedAt = now;
        this.expiresAt = newExpiresAt;
    }

    public void revoke(LocalDateTime now) {
        this.revokedAt = now;
    }

    // 끊기지 않았고 만료 전이면 활성
    public boolean isActive(LocalDateTime now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }
}
