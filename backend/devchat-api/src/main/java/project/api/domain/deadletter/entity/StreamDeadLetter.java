package project.api.domain.deadletter.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 최대 재시도를 넘겨 더 처리하지 않는 스트림 메시지. 원문을 남겨 원인을 확인하고 필요하면 다시 넣는다. */
@Entity
@Table(name = "stream_dead_letter",
    uniqueConstraints = @UniqueConstraint(name = "uk_stream_dead_letter", columnNames = {"stream_key", "record_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StreamDeadLetter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stream_key", nullable = false, length = 100)
    private String streamKey;

    @Column(name = "record_id", nullable = false, length = 50)
    private String recordId;

    @Column(columnDefinition = "MEDIUMTEXT")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public StreamDeadLetter(String streamKey, String recordId, String payload) {
        this.streamKey = streamKey;
        this.recordId = recordId;
        this.payload = payload;
        this.createdAt = LocalDateTime.now();
    }
}
