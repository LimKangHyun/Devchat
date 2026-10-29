package project.api.domain.schedule.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 캘린더 구독(.ics) 토큰. 조회용 해시와 다시 보여 주기 위한 암호문을 같이 저장한다
@Entity
@Table(name = "calendar_feed_token")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CalendarFeedToken {

    @Id
    private Long memberId;

    @Column(nullable = false, unique = true, length = 64, columnDefinition = "char(64)")
    private String tokenHash;

    @Column(nullable = false, length = 512)
    private String tokenEncrypted;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public CalendarFeedToken(Long memberId, String tokenHash, String tokenEncrypted,
                             LocalDateTime createdAt) {
        this.memberId = memberId;
        this.tokenHash = tokenHash;
        this.tokenEncrypted = tokenEncrypted;
        this.createdAt = createdAt;
    }

    // 새로 만들기: 예전 토큰은 바로 무효
    public void regenerate(String tokenHash, String tokenEncrypted, LocalDateTime now) {
        this.tokenHash = tokenHash;
        this.tokenEncrypted = tokenEncrypted;
        this.createdAt = now;
    }
}
