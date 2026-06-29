package project.backend.domain.notification.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import project.backend.domain.member.entity.Member;
import project.backend.domain.member.friend.entity.FriendRequest;

@Entity
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_member_id")
    private Member receiver;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_member_id")
    private Member sender;

    @Enumerated(EnumType.STRING)
    private NotificationType type;

    //알림이 온 도메인 id (채팅방 알림이면 해당 채팅방으로 이동해야함)
    private Long referenceId;

    private boolean isRead = false;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    public void markAsRead() {
        this.isRead = true;
    }

    public static Notification ofFriendRequest(FriendRequest friendRequest) {
        return Notification.builder()
            .receiver(friendRequest.getReceiver())
            .sender(friendRequest.getSender())
            .type(NotificationType.FRIEND_REQUESTED)
            .referenceId(friendRequest.getSender().getId())
            .createdAt(LocalDateTime.now())
            .build();
    }

    public static Notification ofFriendRequestByDecision(FriendRequest friendRequest,
        NotificationType type) {
        return Notification.builder()
            .receiver(friendRequest.getSender())
            .sender(friendRequest.getReceiver())
            .type(type)
            .referenceId(friendRequest.getReceiver().getId())
            .createdAt(LocalDateTime.now())
            .build();
    }

    // 수락한 본인에게 보내는 알림 (읽음 처리된 상태)
    public static Notification ofFriendshipEstablished(FriendRequest friendRequest) {
        return Notification.builder()
            .receiver(friendRequest.getReceiver())  // 수락한 본인
            .sender(friendRequest.getSender())      // 요청자
            .type(NotificationType.WE_ARE_FRIEND_NOW)
            .referenceId(friendRequest.getSender().getId()) // 친구가 된 사람 ID
            .createdAt(LocalDateTime.now())
            .build();
    }

    public static Notification ofStudyApply(Member author, Member applicant, Long postId) {
        return Notification.builder()
            .receiver(author)
            .sender(applicant)
            .type(NotificationType.STUDY_APPLY)
            .referenceId(postId)
            .createdAt(LocalDateTime.now())
            .build();
    }

    public static Notification ofStudyResult(Member applicantMember, Member author, Long postId,
        NotificationType type) {
        return Notification.builder()
            .receiver(applicantMember)
            .sender(author)
            .type(type)
            .referenceId(postId)
            .createdAt(LocalDateTime.now())
            .build();
    }
}
