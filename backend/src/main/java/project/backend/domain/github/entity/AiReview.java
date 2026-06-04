package project.backend.domain.github.entity;

import jakarta.persistence.*;
import lombok.*;
import project.backend.domain.chat.chatroom.entity.ChatRoom;
import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class AiReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id")
    private ChatRoom chatRoom;

    private Integer prNumber;
    private String commitSha;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private AiReviewStatus status = AiReviewStatus.PENDING;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String reviewJson;

    private String errorMessage;

    @Builder.Default
    private boolean githubPublished = false;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public void updateSuccess(String reviewJson) {
        this.status = AiReviewStatus.SUCCESS;
        this.reviewJson = reviewJson;
        this.updatedAt = LocalDateTime.now();
    }

    public void updateFail(String errorMessage) {
        this.status = AiReviewStatus.FAIL;
        this.errorMessage = errorMessage;
        this.updatedAt = LocalDateTime.now();
    }

    public void resetToPending(String commitSha) {
        this.status = AiReviewStatus.PENDING;
        this.commitSha = commitSha;
        this.reviewJson = null;
        this.errorMessage = null;
        this.updatedAt = LocalDateTime.now();
    }

    public void markAsPublished() {
        this.githubPublished = true;
    }
}