package project.api.domain.chat.chatroom.entity;

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
public class FallbackSequenceRecovery {

    @Id
    @Column(name = "room_id")
    private Long roomId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public static FallbackSequenceRecovery of(Long roomId) {
        FallbackSequenceRecovery entity = new FallbackSequenceRecovery();
        entity.roomId = roomId;
        entity.createdAt = LocalDateTime.now();
        return entity;
    }
}