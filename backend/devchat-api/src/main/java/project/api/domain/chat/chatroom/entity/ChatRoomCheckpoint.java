package project.api.domain.chat.chatroom.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "chat_room_checkpoint")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoomCheckpoint {

    @Id
    @Column(name = "room_id")
    private Long roomId;

    @Column(name = "synced_message_id", nullable = false)
    private Long syncedMessageId;

    @Column(name = "cumulative_count", nullable = false)
    private Long cumulativeCount;

    public ChatRoomCheckpoint(Long roomId) {
        this.roomId = roomId;
        this.syncedMessageId = 0L;
        this.cumulativeCount = 0L;
    }

    public void advance(Long newMaxMessageId, long delta) {
        if (newMaxMessageId == null || newMaxMessageId <= this.syncedMessageId) {
            return;
        }
        this.cumulativeCount += delta;
        this.syncedMessageId = newMaxMessageId;
    }
}