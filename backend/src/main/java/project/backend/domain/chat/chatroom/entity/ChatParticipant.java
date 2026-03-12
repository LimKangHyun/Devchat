package project.backend.domain.chat.chatroom.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import project.backend.domain.member.entity.Member;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "chat_participant_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member participant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id")
    private ChatRoom chatRoom;

    private boolean isOwner;

    private boolean isActive = true;

    private LocalDateTime joinAt;

    @Column(name = "unread_count", nullable = false)
    private long unreadCount = 0;

    @Column(name = "last_read_message_id")
    private Long lastReadMessageId;

    @Builder
    public ChatParticipant(Long id, Member participant, ChatRoom chatRoom, boolean isOwner,
        boolean isActive, LocalDateTime joinAt) {
        this.id = id;
        this.participant = participant;
        this.chatRoom = chatRoom;
        this.isOwner = isOwner;
        this.isActive = isActive;
        this.joinAt = joinAt;
    }

    public static ChatParticipant of(Member participant, ChatRoom chatRoom) {
        return ChatParticipant.builder()
            .participant(participant)
            .chatRoom(chatRoom)
            .isActive(true)
            .joinAt(LocalDateTime.now())
            .build();
    }

    public static ChatParticipant createOwner(Member participant, ChatRoom chatRoom) {
        return ChatParticipant.builder()
            .participant(participant)
            .chatRoom(chatRoom)
            .isOwner(true)
            .isActive(true)
            .joinAt(LocalDateTime.now())
            .build();
    }

    public void resetUnreadCount(Long messageId) {
        this.unreadCount = 0;
        this.lastReadMessageId = messageId;
    }

    public void leave() {
        this.isActive = false;
    }

    public void rejoin() {
        this.isActive = true;
    }
}