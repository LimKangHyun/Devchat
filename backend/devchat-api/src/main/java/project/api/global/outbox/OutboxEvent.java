package project.api.global.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "outbox_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {

    private static final int ERROR_MAX_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private OutboxEventType eventType;

    /** 관련 엔티티 id (AI 리뷰면 ai_review.id) */
    @Column(nullable = false)
    private Long aggregateId;

    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxStatus status;

    private int attempts;

    @Column(nullable = false)
    private LocalDateTime nextAttemptAt;

    @Column(length = ERROR_MAX_LENGTH)
    private String lastError;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime sentAt;

    public static OutboxEvent pending(OutboxEventType type, Long aggregateId, String payload, LocalDateTime now) {
        OutboxEvent e = new OutboxEvent();
        e.eventType = type;
        e.aggregateId = aggregateId;
        e.payload = payload;
        e.status = OutboxStatus.PENDING;
        e.attempts = 0;
        e.nextAttemptAt = now;
        e.createdAt = now;
        return e;
    }

    /**
     * 처리할 권한을 잠시 가져간다. lease 동안은 다른 릴레이가 가져가지 않고,
     * 처리 중에 서버가 죽으면 lease가 끝난 뒤 다시 처리된다.
     */
    void lease(LocalDateTime now, Duration lease) {
        this.attempts++;
        this.nextAttemptAt = now.plus(lease);
    }

    void markSent(LocalDateTime now) {
        this.status = OutboxStatus.SENT;
        this.sentAt = now;
        this.lastError = null;
    }

    void scheduleRetry(LocalDateTime nextAttemptAt, String error) {
        this.nextAttemptAt = nextAttemptAt;
        this.lastError = truncate(error);
    }

    void markFailed(String error) {
        this.status = OutboxStatus.FAILED;
        this.lastError = truncate(error);
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() <= ERROR_MAX_LENGTH ? s : s.substring(0, ERROR_MAX_LENGTH);
    }
}
