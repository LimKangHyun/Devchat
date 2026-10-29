package project.api.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuthToken {

    @Id
    private Long memberId;

    @Column(length = 512)
    private String refreshToken;

    @Column(length = 512)
    private String githubAccessToken;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public static AuthToken of(Long memberId, String encryptedRefreshToken,
                               String encryptedGithubAccessToken) {
        AuthToken token = new AuthToken();
        token.memberId = memberId;
        token.refreshToken = encryptedRefreshToken;
        token.githubAccessToken = encryptedGithubAccessToken;
        token.createdAt = LocalDateTime.now();
        return token;
    }

    public void updateRefreshToken(String encryptedRefreshToken) {
        this.refreshToken = encryptedRefreshToken;
    }

    // 옛 토큰을 세션으로 옮긴 뒤 비운다
    public void clearRefreshToken() {
        this.refreshToken = null;
    }

    public void updateGithubAccessToken(String encryptedGithubAccessToken) {
        this.githubAccessToken = encryptedGithubAccessToken;
    }
}